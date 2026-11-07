package com.ramonchancay.vigia.core.server.security

import java.net.InetAddress

/**
 * Which client addresses may call the API. The default allows loopback (adb forward, on-device tools) and the
 * Tailscale range 100.64.0.0/10, as the spec recommends exposing the panel only over Tailscale.
 */
class IpAllowlist(private val ranges: List<Cidr>) {
    /** [address] must be an IP literal (what the server reports as the remote address), never a host name. */
    fun allows(address: String): Boolean {
        val bytes = parseLiteral(address) ?: return false
        return ranges.any { it.contains(bytes) }
    }

    data class Cidr(val network: ByteArray, val prefixLength: Int) {
        fun contains(address: ByteArray): Boolean {
            if (address.size != network.size) return false
            var bits = prefixLength
            for (i in network.indices) {
                if (bits <= 0) return true
                val mask = if (bits >= 8) 0xFF else (0xFF shl (8 - bits)) and 0xFF
                if ((address[i].toInt() and mask) != (network[i].toInt() and mask)) return false
                bits -= 8
            }
            return true
        }

        override fun equals(other: Any?) =
            other is Cidr && network.contentEquals(other.network) && prefixLength == other.prefixLength

        override fun hashCode() = 31 * network.contentHashCode() + prefixLength

        companion object {
            fun parse(text: String): Cidr {
                val (ip, prefix) = text.split('/').let { it[0] to it.getOrNull(1) }
                val bytes = requireNotNull(parseLiteral(ip)) { "Not an IP literal: $ip" }
                return Cidr(bytes, prefix?.toInt() ?: (bytes.size * 8))
            }
        }
    }

    companion object {
        // Declared before DEFAULT: companion properties initialize in order and DEFAULT parses with it.
        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

        val DEFAULT = IpAllowlist(listOf("127.0.0.0/8", "::1/128", "100.64.0.0/10").map(Cidr::parse))

        /** Parses an IPv4/IPv6 literal without DNS; returns null for anything else. */
        internal fun parseLiteral(text: String): ByteArray? {
            val clean = text.removePrefix("[").removeSuffix("]").substringBefore('%')
            val looksLikeIp =
                IPV4.matches(clean) ||
                    (clean.contains(':') && clean.all { it.isLetterOrDigit() || it == ':' || it == '.' })
            if (!looksLikeIp) return null
            if (IPV4.matches(clean) && clean.split('.').any { it.toInt() > 255 }) return null
            return runCatching { InetAddress.getByName(clean).address }.getOrNull()?.let(::unmapIpv4)
        }

        /** Treats ::ffff:a.b.c.d (IPv4 seen through an IPv6 socket) as the IPv4 address. */
        private fun unmapIpv4(bytes: ByteArray): ByteArray {
            val mapped = bytes.size == 16 && (0 until 10).all { bytes[it] == 0.toByte() } &&
                bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
            return if (mapped) bytes.copyOfRange(12, 16) else bytes
        }
    }
}
