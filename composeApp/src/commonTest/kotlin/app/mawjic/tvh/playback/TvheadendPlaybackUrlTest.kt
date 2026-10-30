package app.mawjic.tvh.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TvheadendPlaybackUrlTest {
    @Test
    fun createsTicketUrlFromNativeTvheadendPath() {
        val url = buildTicketedStreamUrl(
            endpoint = TvheadendHttpEndpoint("tvheadend.home", 9981, false),
            path = "/stream/channelid/238",
            ticket = "3F318730EAB0ABE5473B1195712F60F27AF19832",
        )
        assertEquals(
            "http://tvheadend.home:9981/stream/channelid/238?ticket=3F318730EAB0ABE5473B1195712F60F27AF19832",
            url,
        )
    }

    @Test
    fun appliesReverseProxyWebRootAndHttps() {
        val url = buildTicketedStreamUrl(
            endpoint = TvheadendHttpEndpoint("tvheadend.example", 443, true, "/tvh/"),
            path = "/stream/channelid/9",
            ticket = "ABC123",
        )
        assertEquals("https://tvheadend.example:443/tvh/stream/channelid/9?ticket=ABC123", url)
    }

    @Test
    fun doesNotDuplicateExistingWebRoot() {
        val url = buildTicketedStreamUrl(
            endpoint = TvheadendHttpEndpoint("10.10.10.10", 9981, false, "/tvh"),
            path = "/tvh/stream/channelid/9",
            ticket = "ABC123",
        )
        assertEquals("http://10.10.10.10:9981/tvh/stream/channelid/9?ticket=ABC123", url)
    }

    @Test
    fun bracketsIpv6Literal() {
        val url = buildTicketedStreamUrl(
            endpoint = TvheadendHttpEndpoint("2001:db8::10", 9981, false),
            path = "/stream/channelid/9",
            ticket = "ABC123",
        )
        assertEquals("http://[2001:db8::10]:9981/stream/channelid/9?ticket=ABC123", url)
    }

    @Test
    fun percentEncodesTicketAsAQueryComponent() {
        val url = buildTicketedStreamUrl(
            endpoint = TvheadendHttpEndpoint("tvheadend.home"),
            path = "/stream/channelid/9",
            ticket = "a+b/c==",
        )
        assertEquals("http://tvheadend.home:9981/stream/channelid/9?ticket=a%2Bb%2Fc%3D%3D", url)
    }

    @Test
    fun rejectsUrlInsteadOfBareHost() {
        assertFailsWith<IllegalArgumentException> {
            TvheadendHttpEndpoint("http://tvheadend.home")
        }
    }

    @Test
    fun rejectsHostWithPathOrWhitespace() {
        assertFailsWith<IllegalArgumentException> { TvheadendHttpEndpoint("tvheadend.home/tvh") }
        assertFailsWith<IllegalArgumentException> { TvheadendHttpEndpoint("tvheadend home") }
    }
}
