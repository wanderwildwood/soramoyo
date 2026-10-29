package com.wanderwildwood.soramoyo.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress

class LocalHttpTest {

    private fun local(ip: String) = LocalHttp.isLocal(InetAddress.getByName(ip))

    @Test fun privateIpv4RangesAreLocal() {
        for (ip in listOf("10.0.0.1", "10.255.255.254", "172.16.0.1", "172.31.255.1", "192.168.1.50", "169.254.10.10")) {
            assertTrue(ip, local(ip))
        }
    }

    @Test fun publicIpv4IsNot() {
        for (ip in listOf("8.8.8.8", "172.15.0.1", "172.32.0.1", "192.169.1.1", "11.0.0.1", "169.253.1.1", "100.64.0.1", "127.0.0.1", "0.0.0.0")) {
            assertFalse(ip, local(ip))
        }
    }

    @Test fun ipv6UniqueLocalAndLinkLocalAreLocalGlobalIsNot() {
        assertTrue(local("fd12:3456:789a::1"))
        assertTrue(local("fc00::1"))
        assertTrue(local("fe80::1"))
        assertFalse(local("2001:4860:4860::8888"))
        assertFalse(local("::1"))
    }

    @Test fun anInternetNameIsRefusedBeforeAnythingIsSent() {
        // A literal public address needs no lookup, so this fails the same offline.
        assertThrows(LocalHttp.NotLocal::class.java) { LocalHttp.get("8.8.8.8", "/get_livedata_info") }
        assertThrows(LocalHttp.NotLocal::class.java) { LocalHttp.get("http://93.184.216.34:8080/", "/x") }
    }

    @Test fun addressesAreTakenHoweverTheyWereCopied() {
        assertEquals(LocalHttp.Target("192.168.1.50", 80), LocalHttp.parse("192.168.1.50"))
        assertEquals(LocalHttp.Target("192.168.1.50", 80), LocalHttp.parse(" http://192.168.1.50/ "))
        assertEquals(LocalHttp.Target("192.168.1.50", 80), LocalHttp.parse("https://192.168.1.50"))
        assertEquals(LocalHttp.Target("192.168.1.50", 8080), LocalHttp.parse("192.168.1.50:8080/live"))
        assertEquals(LocalHttp.Target("fe80::1", 80), LocalHttp.parse("[fe80::1]"))
        assertEquals(LocalHttp.Target("gw.local", 80), LocalHttp.parse("gw.local"))
        assertThrows(IOException::class.java) { LocalHttp.parse("  ") }
    }

    private fun answer(text: String) = LocalHttp.parseResponse(ByteArrayInputStream(text.toByteArray()))

    @Test fun aLengthedBodyIsRead() {
        assertEquals("{\"a\":1}", answer("HTTP/1.1 200 OK\r\nContent-Length: 7\r\n\r\n{\"a\":1}"))
    }

    @Test fun aChunkedBodyIsRead() {
        assertEquals("hello world", answer("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n"))
    }

    @Test fun aBodyToTheCloseIsRead() {
        assertEquals("abc", answer("HTTP/1.0 200 OK\r\n\r\nabc"))
    }

    @Test fun anythingButOkIsAnError() {
        assertThrows(IOException::class.java) { answer("HTTP/1.1 302 Found\r\nLocation: http://8.8.8.8/\r\n\r\n") }
        assertThrows(IOException::class.java) { answer("HTTP/1.1 500 Oops\r\n\r\n") }
    }
}
