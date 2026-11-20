package net.lumalyte.lg.infrastructure.services

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdvancementXpConnectionsTest {
    @Test fun `IPv4-mapped IPv6 folds to the IPv4 address`() {
        assertEquals("203.0.113.7", AdvancementXpConnections.canonical(InetAddress.getByName("::ffff:203.0.113.7")))
        assertEquals("203.0.113.7", AdvancementXpConnections.canonical(InetAddress.getByName("203.0.113.7")))
    }

    @Test fun `loopback and wildcard addresses can't identify a player`() {
        assertNull(AdvancementXpConnections.canonical(InetAddress.getByName("127.0.0.1")))
        assertNull(AdvancementXpConnections.canonical(InetAddress.getByName("::1")))
        assertNull(AdvancementXpConnections.canonical(InetAddress.getByName("0.0.0.0")))
    }
}
