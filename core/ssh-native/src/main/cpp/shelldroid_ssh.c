#include <jni.h>

JNIEXPORT jstring JNICALL
Java_io_shelldroid_ssh_native_1_LibSsh_nativeVersion(JNIEnv* env, jclass clazz) {
    return (*env)->NewStringUTF(env, "0.0.0-stub");
}
