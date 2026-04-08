#include <jni.h>
#include <libssh/libssh.h>

JNIEXPORT jstring JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeVersion(JNIEnv* env, jclass clazz) {
    (void)clazz;
    const char* ver = ssh_version(0);
    return (*env)->NewStringUTF(env, ver ? ver : "unknown");
}
