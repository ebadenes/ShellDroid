package io.shelldroid.ssh.native_

object LibSsh {
    init {
        System.loadLibrary("shelldroid_ssh")
    }

    @JvmStatic
    external fun nativeVersion(): String
}
