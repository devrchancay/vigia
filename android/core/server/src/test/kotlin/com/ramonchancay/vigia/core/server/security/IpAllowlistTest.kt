package com.ramonchancay.vigia.core.server.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpAllowlistTest {
    private val allowlist = IpAllowlist.DEFAULT

    @Test
    fun `allows loopback and the tailscale range`() {
        listOf(
            "127.0.0.1",
            "127.8.9.10",
            "::1",
            "[::1]",
            "100.64.0.1",
            "100.101.102.103",
            "100.127.255.255",
            "::ffff:100.64.0.7"
        )
            .forEach { assertTrue(it, allowlist.allows(it)) }
    }

    @Test
    fun `rejects lan, public and edge addresses`() {
        listOf("192.168.1.20", "10.0.0.5", "8.8.8.8", "100.63.255.255", "100.128.0.0", "fe80::1", "2001:db8::1")
            .forEach { assertFalse(it, allowlist.allows(it)) }
    }

    @Test
    fun `never resolves host names`() {
        listOf("localhost", "example.com", "", "999.1.1.1", "1.2.3")
            .forEach { assertFalse(it, allowlist.allows(it)) }
    }

    @Test
    fun `custom ranges with odd prefix lengths`() {
        val custom = IpAllowlist(listOf(IpAllowlist.Cidr.parse("192.168.1.0/25")))

        assertTrue(custom.allows("192.168.1.127"))
        assertFalse(custom.allows("192.168.1.128"))
    }
}
