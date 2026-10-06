package com.dani.assistant.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SigningCheckTest {
    @Test fun sha256KnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", SigningCheck.sha256Hex("abc".toByteArray()))
    }

    @Test fun releaseFingerprintFormat() {
        assertEquals(64, SigningCheck.RELEASE_SHA256.length)
        assertTrue(SigningCheck.RELEASE_SHA256.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun unknownCertIsNotRelease() {
        assertFalse(SigningCheck.isRelease(listOf("debug-cert".toByteArray())))
        assertFalse(SigningCheck.isRelease(emptyList()))
    }
}
