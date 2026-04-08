package io.shelldroid.core.ssh

import com.google.common.truth.Truth.assertThat
import io.shelldroid.core.ssh.model.AuthMethod
import org.junit.Test

class AuthMethodTest {

    @Test fun `password equality uses contentEquals`() {
        val a = AuthMethod.Password(charArrayOf('a', 'b', 'c'))
        val b = AuthMethod.Password(charArrayOf('a', 'b', 'c'))
        val c = AuthMethod.Password(charArrayOf('x', 'y', 'z'))

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a).isNotEqualTo(c)
    }

    @Test fun `password toString does not leak secret`() {
        val pw = AuthMethod.Password(charArrayOf('s', 'e', 'c'))
        assertThat(pw.toString()).doesNotContain("sec")
        assertThat(pw.toString()).contains("***")
    }

    @Test fun `publickey equality uses contentEquals on both fields`() {
        val a = AuthMethod.PublicKey(byteArrayOf(1, 2, 3), charArrayOf('p', 'w'))
        val b = AuthMethod.PublicKey(byteArrayOf(1, 2, 3), charArrayOf('p', 'w'))
        val c = AuthMethod.PublicKey(byteArrayOf(1, 2, 3), null)

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a).isNotEqualTo(c)
    }

    @Test fun `methodName matches sealed kind`() {
        assertThat(AuthMethod.Password(charArrayOf()).methodName).isEqualTo("password")
        assertThat(AuthMethod.PublicKey(byteArrayOf(), null).methodName).isEqualTo("publickey")
    }
}
