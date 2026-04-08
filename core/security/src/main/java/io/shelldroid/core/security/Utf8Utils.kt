package io.shelldroid.core.security

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

/**
 * UTF-8 encoding/decoding helpers that avoid going through [String], so secrets
 * never end up sitting in the immutable String pool. Both functions return
 * fresh arrays the caller can zeroize via [CharArray.fill] / [ByteArray.fill].
 */

fun charsToUtf8Bytes(chars: CharArray): ByteArray {
    val charBuffer = CharBuffer.wrap(chars)
    val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
    val bytes = ByteArray(byteBuffer.remaining())
    byteBuffer.get(bytes)
    return bytes
}

fun utf8BytesToChars(bytes: ByteArray): CharArray {
    val byteBuffer = ByteBuffer.wrap(bytes)
    val charBuffer = StandardCharsets.UTF_8.decode(byteBuffer)
    val chars = CharArray(charBuffer.remaining())
    charBuffer.get(chars)
    return chars
}
