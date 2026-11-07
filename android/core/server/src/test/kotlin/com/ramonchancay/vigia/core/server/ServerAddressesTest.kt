package com.ramonchancay.vigia.core.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressesTest {
    @Test
    fun `recognizes the tailscale range`() {
        assertTrue(ServerAddresses.isTailscale("100.64.0.1"))
        assertTrue(ServerAddresses.isTailscale("100.127.1.2"))
        assertFalse(ServerAddresses.isTailscale("100.128.0.1"))
        assertFalse(ServerAddresses.isTailscale("192.168.1.10"))
    }

    @Test
    fun `puts tailscale addresses first and drops duplicates`() {
        assertEquals(
            listOf("100.101.1.2", "192.168.1.10"),
            ServerAddresses.sortTailscaleFirst(listOf("192.168.1.10", "100.101.1.2", "192.168.1.10"))
        )
    }
}
