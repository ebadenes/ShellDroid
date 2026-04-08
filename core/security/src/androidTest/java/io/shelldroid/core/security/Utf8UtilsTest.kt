package io.shelldroid.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Utf8UtilsTest {

    @Test
    fun roundtrip_ascii() {
        val original = "hello world".toCharArray()
        val bytes = charsToUtf8Bytes(original)
        val back = utf8BytesToChars(bytes)
        assertThat(back).isEqualTo(original)
    }

    @Test
    fun roundtrip_multibyte() {
        val original = "héllo ñandú 漢字 🚀".toCharArray()
        val bytes = charsToUtf8Bytes(original)
        val back = utf8BytesToChars(bytes)
        assertThat(back).isEqualTo(original)
    }

    @Test
    fun roundtrip_empty() {
        val bytes = charsToUtf8Bytes(CharArray(0))
        assertThat(bytes).isEmpty()
        assertThat(utf8BytesToChars(bytes)).isEmpty()
    }
}
