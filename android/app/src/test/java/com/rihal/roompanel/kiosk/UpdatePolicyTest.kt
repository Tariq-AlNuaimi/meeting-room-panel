package com.rihal.roompanel.kiosk

import com.rihal.roompanel.data.api.AppUpdateDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private val hash = "ab".repeat(32)
    private fun update(code: Int, url: String = "https://example.com/app.apk", sha: String = hash) = AppUpdateDto(code, url, sha)

    @Test
    fun `installs only a strictly newer version`() {
        assertTrue(UpdatePolicy.shouldInstall(2, update(3)))
        assertFalse(UpdatePolicy.shouldInstall(3, update(3)))
        assertFalse(UpdatePolicy.shouldInstall(4, update(3)))
        assertFalse(UpdatePolicy.shouldInstall(2, null))
    }

    @Test
    fun `refuses plain http and malformed hashes`() {
        assertFalse(UpdatePolicy.shouldInstall(1, update(3, url = "http://example.com/app.apk")))
        assertFalse(UpdatePolicy.shouldInstall(1, update(3, sha = "abc")))
        assertTrue(UpdatePolicy.shouldInstall(1, update(3, sha = hash.uppercase())))
    }

    @Test
    fun `sha256 matches the known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UpdatePolicy.sha256Hex("abc".byteInputStream()),
        )
    }
}
