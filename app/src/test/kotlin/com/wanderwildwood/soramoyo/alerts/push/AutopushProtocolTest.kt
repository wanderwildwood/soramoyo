package com.wanderwildwood.soramoyo.alerts.push

import com.wanderwildwood.soramoyo.alerts.push.AutopushProtocol.Server
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test



class AutopushProtocolTest {

    @Test
    fun readsWhatTheServerSends() {
        assertEquals(Server.Hello("u-1", 200), Server.parse("""{"messageType":"hello","uaid":"u-1","status":200}"""))
        assertEquals(
            Server.Register("c-1", 200, "https://push.example.org/wpush/v2/abc"),
            Server.parse("""{"messageType":"register","channelID":"c-1","status":200,"pushEndpoint":"https://push.example.org/wpush/v2/abc"}"""),
        )
        assertEquals(Server.Notification("c-1", "v-9", "AAEC"), Server.parse("""{"messageType":"notification","channelID":"c-1","version":"v-9","data":"AAEC"}"""))
        assertEquals(Server.Notification("c-1", "v-9", null), Server.parse("""{"messageType":"notification","channelID":"c-1","version":"v-9"}"""))
        assertEquals(Server.Ping, Server.parse("{}"))
        assertEquals(Server.Other("broadcast"), Server.parse("""{"messageType":"broadcast"}"""))
        assertNull(Server.parse("not json"))
        assertNull(Server.parse("""{"messageType":"hello"}"""))
    }

    @Test
    fun writesWhatTheServerExpects() {
        val c = AutopushProtocol.Client
        assertEquals("hello", JSONObject(c.hello(null)).getString("messageType"))
        assertEquals("u-1", JSONObject(c.hello("u-1")).getString("uaid"))
        val reg = JSONObject(c.register("c-1", "KEY"))
        assertEquals("c-1", reg.getString("channelID"))
        assertEquals("KEY", reg.getString("key"))
        val ack = JSONObject(c.ack("c-1", "v-9")).getJSONArray("updates").getJSONObject(0)
        assertEquals("v-9", ack.getString("version"))
    }

    @Test
    fun waitsLongerAfterEachFailure() {
        assertEquals(1_000L, AutopushProtocol.retryDelayMillis(1))
        assertEquals(600_000L, AutopushProtocol.retryDelayMillis(6))
        assertEquals(900_000L, AutopushProtocol.retryDelayMillis(20))
    }
}
