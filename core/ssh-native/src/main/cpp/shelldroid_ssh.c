#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/syscall.h>
#include <android/log.h>
#include <libssh/libssh.h>
#include <mbedtls/ctr_drbg.h>

#define LOG_TAG "shelldroid_ssh"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* Forward declarations from libssh internal pki.h — these symbols are
 * present in the static archive even though not in the public LIBSSH_API. */
extern int ssh_pki_export_pubkey_blob(const ssh_key key, ssh_string *pblob);
extern int ssh_pki_import_pubkey_blob(const ssh_string key_blob, ssh_key *pkey);

/* libssh exposes its global CTR_DRBG so we can reseed it after the
 * broken-on-Android init path in ssh_crypto_init(). */
extern mbedtls_ctr_drbg_context *ssh_get_mbedtls_ctr_drbg_context(void);

/* ------------------------------------------------------------------
 * Android entropy source.
 *
 * mbedtls's platform entropy on Android bionic falls through to
 * fopen("/dev/urandom") because HAVE_GETRANDOM is gated on __GLIBC__
 * (bionic is NOT glibc). That path fails in libssh's first seed,
 * and libssh then frees the DRBG context but STILL marks init=1
 * and returns SSH_OK (bug in libssh 0.11 libmbedcrypto.c:1066-1100).
 *
 * The next ssh_mbedtls_random() dereferences a NULL f_entropy inside
 * mbedtls_ctr_drbg_reseed_internal, producing SIGSEGV during the
 * curve25519 KEX.
 *
 * Workaround: after ssh_init(), re-seed libssh's global CTR_DRBG
 * ourselves with a custom entropy callback that uses getrandom(2)
 * directly (Android bionic exposes it since API 28, plus syscall
 * fallback). We also keep /dev/urandom read(2) as a second fallback.
 * ------------------------------------------------------------------ */
static int shelldroid_entropy(void *data, unsigned char *output, size_t len) {
    (void)data;
    size_t got = 0;
    while (got < len) {
        ssize_t n;
#if defined(SYS_getrandom)
        n = (ssize_t)syscall(SYS_getrandom, output + got, len - got, 0);
#else
        n = -1; errno = ENOSYS;
#endif
        if (n > 0) {
            got += (size_t)n;
            continue;
        }
        if (n < 0 && errno == EINTR) continue;
        /* Fallback: /dev/urandom via open/read (never fopen). */
        int fd = open("/dev/urandom", O_RDONLY | O_CLOEXEC);
        if (fd < 0) return -1;
        while (got < len) {
            ssize_t r = read(fd, output + got, len - got);
            if (r > 0) { got += (size_t)r; continue; }
            if (r < 0 && errno == EINTR) continue;
            close(fd);
            return -1;
        }
        close(fd);
        break;
    }
    return 0;
}

/* Forward declare libssh's threading API so we can force pthread
 * callbacks explicitly at load time. libssh 0.11's default behaviour
 * on POSIX is to pick pthread, but we've been hit by concurrency
 * races that look like the default didn't kick in. Being explicit is
 * cheap insurance. */
struct ssh_threads_callbacks_struct;
extern struct ssh_threads_callbacks_struct *ssh_threads_get_pthread(void);
extern int ssh_threads_set_callbacks(struct ssh_threads_callbacks_struct *cb);

/* ------------------------------------------------------------------
 * JNI_OnLoad — called once when System.loadLibrary() completes.
 * ------------------------------------------------------------------ */
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm; (void)reserved;

    /* Enable pthread locking in libssh BEFORE ssh_init. */
    ssh_threads_set_callbacks(ssh_threads_get_pthread());

    /* Let libssh run its normal init. On Android this silently corrupts
     * the DRBG (see comment above); we repair it right after. */
    ssh_init();

    mbedtls_ctr_drbg_context *ctx = ssh_get_mbedtls_ctr_drbg_context();
    if (ctx != NULL) {
        /* Free whatever libssh left behind and re-seed with a working
         * entropy source. ctr_drbg_free is safe to call twice. */
        mbedtls_ctr_drbg_free(ctx);
        mbedtls_ctr_drbg_init(ctx);
        int rc = mbedtls_ctr_drbg_seed(ctx, shelldroid_entropy, NULL, NULL, 0);
        if (rc != 0) {
            LOGE("mbedtls_ctr_drbg_seed failed: -0x%x", -rc);
        } else {
            LOGI("shelldroid CTR_DRBG seeded via getrandom/urandom");
        }
    } else {
        LOGE("ssh_get_mbedtls_ctr_drbg_context returned NULL");
    }

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM* vm, void* reserved) {
    (void)vm; (void)reserved;
    ssh_finalize();
}

/* ------------------------------------------------------------------
 * helpers
 * ------------------------------------------------------------------ */
static void zeroize(void* p, size_t n) {
    if (!p || n == 0) return;
    volatile unsigned char* v = (volatile unsigned char*)p;
    while (n--) *v++ = 0;
}

static char* jstring_to_cstr(JNIEnv* env, jstring s) {
    if (!s) return NULL;
    const char* utf = (*env)->GetStringUTFChars(env, s, NULL);
    if (!utf) return NULL;
    char* dup = strdup(utf);
    (*env)->ReleaseStringUTFChars(env, s, utf);
    return dup;
}

/* ------------------------------------------------------------------
 * version
 * ------------------------------------------------------------------ */
JNIEXPORT jstring JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeVersion(JNIEnv* env, jclass clazz) {
    (void)clazz;
    const char* ver = ssh_version(0);
    return (*env)->NewStringUTF(env, ver ? ver : "unknown");
}

/* ------------------------------------------------------------------
 * session lifecycle
 * ------------------------------------------------------------------ */
JNIEXPORT jlong JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeNewSession(JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    ssh_session s = ssh_new();
    return (jlong)(intptr_t)s;
}

JNIEXPORT void JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeFreeSession(JNIEnv* env, jclass clazz, jlong sessionPtr) {
    (void)env; (void)clazz;
    ssh_session s = (ssh_session)(intptr_t)sessionPtr;
    if (s) ssh_free(s);
}

/* ------------------------------------------------------------------
 * options
 * ------------------------------------------------------------------ */
JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeSetOptionString(JNIEnv* env, jclass clazz,
        jlong sessionPtr, jstring option, jstring value) {
    (void)clazz;
    ssh_session s = (ssh_session)(intptr_t)sessionPtr;
    if (!s || !option) return SSH_ERROR;

    const char* opt = (*env)->GetStringUTFChars(env, option, NULL);
    const char* val = value ? (*env)->GetStringUTFChars(env, value, NULL) : NULL;

    enum ssh_options_e o;
    int rc = SSH_OK;
    if (strcmp(opt, "host") == 0)            o = SSH_OPTIONS_HOST;
    else if (strcmp(opt, "user") == 0)       o = SSH_OPTIONS_USER;
    else if (strcmp(opt, "knownhosts") == 0) o = SSH_OPTIONS_KNOWNHOSTS;
    else { rc = SSH_ERROR; goto done; }

    rc = ssh_options_set(s, o, val);
done:
    (*env)->ReleaseStringUTFChars(env, option, opt);
    if (val) (*env)->ReleaseStringUTFChars(env, value, val);
    return rc;
}

JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeSetOptionInt(JNIEnv* env, jclass clazz,
        jlong sessionPtr, jstring option, jint value) {
    (void)clazz;
    ssh_session s = (ssh_session)(intptr_t)sessionPtr;
    if (!s || !option) return SSH_ERROR;
    const char* opt = (*env)->GetStringUTFChars(env, option, NULL);
    int rc = SSH_OK;
    if (strcmp(opt, "port") == 0) {
        /* SSH_OPTIONS_PORT expects unsigned int* */
        unsigned int v = (unsigned int)value;
        rc = ssh_options_set(s, SSH_OPTIONS_PORT, &v);
    } else if (strcmp(opt, "timeout") == 0) {
        /* SSH_OPTIONS_TIMEOUT expects long* (seconds), NOT int* */
        long v = (long)value;
        rc = ssh_options_set(s, SSH_OPTIONS_TIMEOUT, &v);
    } else {
        rc = SSH_ERROR;
    }
    (*env)->ReleaseStringUTFChars(env, option, opt);
    return rc;
}

/* ------------------------------------------------------------------
 * connection
 * ------------------------------------------------------------------ */
JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeConnect(JNIEnv* env, jclass clazz, jlong sessionPtr) {
    (void)env; (void)clazz;
    ssh_session s = (ssh_session)(intptr_t)sessionPtr;
    if (!s) return SSH_ERROR;
    return ssh_connect(s);
}

JNIEXPORT void JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeDisconnect(JNIEnv* env, jclass clazz, jlong sessionPtr) {
    (void)env; (void)clazz;
    ssh_session s = (ssh_session)(intptr_t)sessionPtr;
    if (s) ssh_disconnect(s);
}

JNIEXPORT jstring JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeGetError(JNIEnv* env, jclass clazz, jlong sessionPtr) {
    (void)clazz;
    ssh_session s = (ssh_session)(intptr_t)sessionPtr;
    if (!s) return (*env)->NewStringUTF(env, "null session");
    const char* msg = ssh_get_error(s);
    return (*env)->NewStringUTF(env, msg ? msg : "");
}

/* ------------------------------------------------------------------
 * host key
 * ------------------------------------------------------------------ */
JNIEXPORT jbyteArray JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeGetServerPublicKey(JNIEnv* env, jclass clazz, jlong sessionPtr) {
    (void)clazz;
    ssh_session s = (ssh_session)(intptr_t)sessionPtr;
    if (!s) return NULL;

    ssh_key key = NULL;
    if (ssh_get_server_publickey(s, &key) != SSH_OK || !key) return NULL;

    ssh_string skey = NULL;
    if (ssh_pki_export_pubkey_blob(key, &skey) != SSH_OK || !skey) {
        ssh_key_free(key);
        return NULL;
    }
    unsigned char* blob = (unsigned char*)ssh_string_data(skey);
    size_t blob_len = ssh_string_len(skey);

    jbyteArray arr = (*env)->NewByteArray(env, (jsize)blob_len);
    if (arr) {
        (*env)->SetByteArrayRegion(env, arr, 0, (jsize)blob_len, (const jbyte*)blob);
    }
    ssh_string_free(skey);
    ssh_key_free(key);
    return arr;
}

JNIEXPORT jstring JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeGetPublicKeyType(JNIEnv* env, jclass clazz, jbyteArray keyBlob) {
    (void)clazz;
    if (!keyBlob) return (*env)->NewStringUTF(env, "");

    jsize len = (*env)->GetArrayLength(env, keyBlob);
    jbyte* bytes = (*env)->GetByteArrayElements(env, keyBlob, NULL);

    ssh_key key = NULL;
    ssh_string skey = ssh_string_new((size_t)len);
    if (!skey) {
        (*env)->ReleaseByteArrayElements(env, keyBlob, bytes, JNI_ABORT);
        return (*env)->NewStringUTF(env, "");
    }
    ssh_string_fill(skey, bytes, (size_t)len);
    if (ssh_pki_import_pubkey_blob(skey, &key) != SSH_OK || !key) {
        ssh_string_free(skey);
        (*env)->ReleaseByteArrayElements(env, keyBlob, bytes, JNI_ABORT);
        return (*env)->NewStringUTF(env, "");
    }
    const char* type_str = ssh_key_type_to_char(ssh_key_type(key));
    jstring out = (*env)->NewStringUTF(env, type_str ? type_str : "");
    ssh_key_free(key);
    ssh_string_free(skey);
    (*env)->ReleaseByteArrayElements(env, keyBlob, bytes, JNI_ABORT);
    return out;
}

JNIEXPORT jbyteArray JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeGetPublicKeyHashSha256(JNIEnv* env, jclass clazz, jbyteArray keyBlob) {
    (void)clazz;
    if (!keyBlob) return NULL;

    jsize len = (*env)->GetArrayLength(env, keyBlob);
    jbyte* bytes = (*env)->GetByteArrayElements(env, keyBlob, NULL);

    ssh_string skey = ssh_string_new((size_t)len);
    if (!skey) {
        (*env)->ReleaseByteArrayElements(env, keyBlob, bytes, JNI_ABORT);
        return NULL;
    }
    ssh_string_fill(skey, bytes, (size_t)len);

    ssh_key key = NULL;
    if (ssh_pki_import_pubkey_blob(skey, &key) != SSH_OK || !key) {
        ssh_string_free(skey);
        (*env)->ReleaseByteArrayElements(env, keyBlob, bytes, JNI_ABORT);
        return NULL;
    }

    unsigned char* hash = NULL;
    size_t hlen = 0;
    if (ssh_get_publickey_hash(key, SSH_PUBLICKEY_HASH_SHA256, &hash, &hlen) != 0) {
        ssh_key_free(key);
        ssh_string_free(skey);
        (*env)->ReleaseByteArrayElements(env, keyBlob, bytes, JNI_ABORT);
        return NULL;
    }

    jbyteArray out = (*env)->NewByteArray(env, (jsize)hlen);
    if (out) {
        (*env)->SetByteArrayRegion(env, out, 0, (jsize)hlen, (const jbyte*)hash);
    }
    ssh_clean_pubkey_hash(&hash);
    ssh_key_free(key);
    ssh_string_free(skey);
    (*env)->ReleaseByteArrayElements(env, keyBlob, bytes, JNI_ABORT);
    return out;
}

/* ------------------------------------------------------------------
 * auth
 * ------------------------------------------------------------------ */
JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeAuthPassword(JNIEnv* env, jclass clazz,
        jlong sessionPtr, jstring username, jbyteArray passwordBytes) {
    (void)clazz;
    ssh_session s = (ssh_session)(intptr_t)sessionPtr;
    if (!s) return SSH_ERROR;

    const char* user = username ? (*env)->GetStringUTFChars(env, username, NULL) : NULL;

    jsize plen = passwordBytes ? (*env)->GetArrayLength(env, passwordBytes) : 0;
    char* pwbuf = NULL;
    if (plen > 0) {
        pwbuf = (char*)malloc((size_t)plen + 1);
        if (!pwbuf) {
            if (user) (*env)->ReleaseStringUTFChars(env, username, user);
            return SSH_ERROR;
        }
        (*env)->GetByteArrayRegion(env, passwordBytes, 0, plen, (jbyte*)pwbuf);
        pwbuf[plen] = '\0';
    } else {
        pwbuf = strdup("");
    }

    int rc = ssh_userauth_password(s, user, pwbuf);

    /* zeroize before free */
    zeroize(pwbuf, (size_t)plen);
    free(pwbuf);
    if (user) (*env)->ReleaseStringUTFChars(env, username, user);
    return rc;
}

JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeAuthPrivateKey(JNIEnv* env, jclass clazz,
        jlong sessionPtr, jstring username, jbyteArray privateKeyPem, jbyteArray passphraseBytes) {
    (void)clazz;
    ssh_session s = (ssh_session)(intptr_t)sessionPtr;
    if (!s || !privateKeyPem) return SSH_ERROR;

    const char* user = username ? (*env)->GetStringUTFChars(env, username, NULL) : NULL;

    jsize klen = (*env)->GetArrayLength(env, privateKeyPem);
    char* keybuf = (char*)malloc((size_t)klen + 1);
    if (!keybuf) {
        if (user) (*env)->ReleaseStringUTFChars(env, username, user);
        return SSH_ERROR;
    }
    (*env)->GetByteArrayRegion(env, privateKeyPem, 0, klen, (jbyte*)keybuf);
    keybuf[klen] = '\0';

    char* passbuf = NULL;
    jsize pplen = 0;
    if (passphraseBytes) {
        pplen = (*env)->GetArrayLength(env, passphraseBytes);
        passbuf = (char*)malloc((size_t)pplen + 1);
        if (passbuf) {
            (*env)->GetByteArrayRegion(env, passphraseBytes, 0, pplen, (jbyte*)passbuf);
            passbuf[pplen] = '\0';
        }
    }

    ssh_key pkey = NULL;
    int rc = ssh_pki_import_privkey_base64(keybuf, passbuf, NULL, NULL, &pkey);
    if (rc != SSH_OK || !pkey) {
        rc = SSH_ERROR;
        goto cleanup;
    }
    rc = ssh_userauth_publickey(s, user, pkey);

cleanup:
    if (pkey) ssh_key_free(pkey);
    zeroize(keybuf, (size_t)klen);
    free(keybuf);
    if (passbuf) {
        zeroize(passbuf, (size_t)pplen);
        free(passbuf);
    }
    if (user) (*env)->ReleaseStringUTFChars(env, username, user);
    return rc;
}

/* ------------------------------------------------------------------
 * channel
 * ------------------------------------------------------------------ */
JNIEXPORT jlong JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeNewChannel(JNIEnv* env, jclass clazz, jlong sessionPtr) {
    (void)env; (void)clazz;
    ssh_session s = (ssh_session)(intptr_t)sessionPtr;
    if (!s) return 0L;
    ssh_channel c = ssh_channel_new(s);
    return (jlong)(intptr_t)c;
}

JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeOpenSession(JNIEnv* env, jclass clazz, jlong channelPtr) {
    (void)env; (void)clazz;
    ssh_channel c = (ssh_channel)(intptr_t)channelPtr;
    if (!c) return SSH_ERROR;
    return ssh_channel_open_session(c);
}

JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeRequestPty(JNIEnv* env, jclass clazz,
        jlong channelPtr, jstring term, jint cols, jint rows) {
    (void)clazz;
    ssh_channel c = (ssh_channel)(intptr_t)channelPtr;
    if (!c) return SSH_ERROR;
    const char* t = term ? (*env)->GetStringUTFChars(env, term, NULL) : "xterm";
    int rc = ssh_channel_request_pty_size(c, t, (int)cols, (int)rows);
    if (term) (*env)->ReleaseStringUTFChars(env, term, t);
    return rc;
}

JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeChangePtySize(JNIEnv* env, jclass clazz,
        jlong channelPtr, jint cols, jint rows) {
    (void)env; (void)clazz;
    ssh_channel c = (ssh_channel)(intptr_t)channelPtr;
    if (!c) return SSH_ERROR;
    return ssh_channel_change_pty_size(c, (int)cols, (int)rows);
}

JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeShell(JNIEnv* env, jclass clazz, jlong channelPtr) {
    (void)env; (void)clazz;
    ssh_channel c = (ssh_channel)(intptr_t)channelPtr;
    if (!c) return SSH_ERROR;
    return ssh_channel_request_shell(c);
}

JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeChannelRead(JNIEnv* env, jclass clazz,
        jlong channelPtr, jbyteArray buffer, jint isStderr) {
    (void)clazz;
    ssh_channel c = (ssh_channel)(intptr_t)channelPtr;
    if (!c || !buffer) return SSH_ERROR;
    jsize cap = (*env)->GetArrayLength(env, buffer);
    jbyte* buf = (*env)->GetByteArrayElements(env, buffer, NULL);
    int n = ssh_channel_read(c, buf, (uint32_t)cap, isStderr ? 1 : 0);
    (*env)->ReleaseByteArrayElements(env, buffer, buf, 0);
    return n;
}

/*
 * Polling read with a timeout in milliseconds. Returns the number of bytes
 * read, 0 on timeout, or negative on error/EOF. Using this from the Kotlin
 * reader loop lets it observe coroutine cancellation without having to
 * close the channel from another thread mid-read (which corrupts libssh's
 * per-session cipher state and makes subsequent ssh_channel_open_session
 * return garbage packet lengths).
 */
JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeChannelReadTimeout(JNIEnv* env, jclass clazz,
        jlong channelPtr, jbyteArray buffer, jint isStderr, jint timeoutMs) {
    (void)clazz;
    ssh_channel c = (ssh_channel)(intptr_t)channelPtr;
    if (!c || !buffer) return SSH_ERROR;
    jsize cap = (*env)->GetArrayLength(env, buffer);
    jbyte* buf = (*env)->GetByteArrayElements(env, buffer, NULL);
    int n = ssh_channel_read_timeout(c, buf, (uint32_t)cap, isStderr ? 1 : 0, timeoutMs);
    (*env)->ReleaseByteArrayElements(env, buffer, buf, 0);
    return n;
}

/*
 * Non-blocking read. Returns 0 immediately if no data is buffered yet,
 * positive byte count if data is available, or SSH_ERROR on error.
 * Caller must check ssh_channel_is_eof separately to distinguish a
 * legitimate zero return from end-of-stream. This is the pattern
 * JuiceSSH uses (adaptive sleep backoff) and is significantly less
 * contended with writes than ssh_channel_read_timeout.
 */
JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeChannelReadNonblocking(JNIEnv* env, jclass clazz,
        jlong channelPtr, jbyteArray buffer, jint isStderr) {
    (void)clazz;
    ssh_channel c = (ssh_channel)(intptr_t)channelPtr;
    if (!c || !buffer) return SSH_ERROR;
    jsize cap = (*env)->GetArrayLength(env, buffer);
    jbyte* buf = (*env)->GetByteArrayElements(env, buffer, NULL);
    int n = ssh_channel_read_nonblocking(c, buf, (uint32_t)cap, isStderr ? 1 : 0);
    (*env)->ReleaseByteArrayElements(env, buffer, buf, 0);
    return n;
}

JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeChannelIsEof(JNIEnv* env, jclass clazz,
        jlong channelPtr) {
    (void)env; (void)clazz;
    ssh_channel c = (ssh_channel)(intptr_t)channelPtr;
    if (!c) return 1;
    return ssh_channel_is_eof(c);
}

JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeChannelWrite(JNIEnv* env, jclass clazz,
        jlong channelPtr, jbyteArray data, jint offset, jint length) {
    (void)clazz;
    ssh_channel c = (ssh_channel)(intptr_t)channelPtr;
    if (!c || !data) return SSH_ERROR;
    jbyte* buf = (*env)->GetByteArrayElements(env, data, NULL);
    int n = ssh_channel_write(c, buf + offset, (uint32_t)length);
    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
    if (n < 0) {
        ssh_session s = ssh_channel_get_session(c);
        const char* err = s ? ssh_get_error(s) : "(no session)";
        LOGE("ssh_channel_write(len=%d) failed: rc=%d err=%s", length, n, err ? err : "(null)");
    }
    return n;
}

JNIEXPORT void JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeChannelClose(JNIEnv* env, jclass clazz, jlong channelPtr) {
    (void)env; (void)clazz;
    ssh_channel c = (ssh_channel)(intptr_t)channelPtr;
    if (c) {
        ssh_channel_send_eof(c);
        ssh_channel_close(c);
    }
}

JNIEXPORT void JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeChannelFree(JNIEnv* env, jclass clazz, jlong channelPtr) {
    (void)env; (void)clazz;
    ssh_channel c = (ssh_channel)(intptr_t)channelPtr;
    if (c) ssh_channel_free(c);
}

/* ------------------------------------------------------------------
 * port forwarding (direct-tcpip channel for LOCAL forwarding)
 * ------------------------------------------------------------------ */

/**
 * Opens a direct-tcpip channel (LOCAL port forward). The returned channel
 * pointer can be used with the regular nativeChannelRead / nativeChannelWrite
 * functions to pipe data between the local socket and the remote endpoint.
 *
 * Returns the channel pointer (as jlong), or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeOpenForward(JNIEnv* env, jclass clazz,
        jlong sessionPtr, jstring remoteHost, jint remotePort,
        jstring sourceHost, jint localPort) {
    (void)clazz;
    ssh_session s = (ssh_session)(intptr_t)sessionPtr;
    if (!s) return 0;

    char* rh = jstring_to_cstr(env, remoteHost);
    if (!rh) return 0;
    char* sh = jstring_to_cstr(env, sourceHost);
    if (!sh) sh = strdup("localhost");

    ssh_channel ch = ssh_channel_new(s);
    if (!ch) {
        free(rh);
        free(sh);
        return 0;
    }

    int rc = ssh_channel_open_forward(ch, rh, (int)remotePort, sh, (int)localPort);
    free(rh);
    free(sh);

    if (rc != SSH_OK) {
        LOGE("ssh_channel_open_forward failed: %s", ssh_get_error(s));
        ssh_channel_free(ch);
        return 0;
    }
    return (jlong)(intptr_t)ch;
}
