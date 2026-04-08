#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <libssh/libssh.h>

/* Forward declarations from libssh internal pki.h — these symbols are
 * present in the static archive even though not in the public LIBSSH_API. */
extern int ssh_pki_export_pubkey_blob(const ssh_key key, ssh_string *pblob);
extern int ssh_pki_import_pubkey_blob(const ssh_string key_blob, ssh_key *pkey);

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
    int v = (int)value;
    int rc = SSH_OK;
    enum ssh_options_e o;
    if (strcmp(opt, "port") == 0)         o = SSH_OPTIONS_PORT;
    else if (strcmp(opt, "timeout") == 0) o = SSH_OPTIONS_TIMEOUT;
    else { rc = SSH_ERROR; goto done; }
    rc = ssh_options_set(s, o, &v);
done:
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

JNIEXPORT jint JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeChannelWrite(JNIEnv* env, jclass clazz,
        jlong channelPtr, jbyteArray data, jint offset, jint length) {
    (void)clazz;
    ssh_channel c = (ssh_channel)(intptr_t)channelPtr;
    if (!c || !data) return SSH_ERROR;
    jbyte* buf = (*env)->GetByteArrayElements(env, data, NULL);
    int n = ssh_channel_write(c, buf + offset, (uint32_t)length);
    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
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
