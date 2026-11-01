package io.autoaps.core

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NightscoutClientTest {
    private val server = MockWebServer()
    private fun fixture(name: String) = javaClass.getResource("/$name")!!.readText()

    @BeforeTest fun start() = server.start()
    @AfterTest fun stop() = server.shutdown()

    private fun respond(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = handler(request)
        }
    }

    @Test
    fun `sends token and reads properties`() {
        respond { req ->
            when {
                req.path!!.startsWith("/api/v1/status.json") -> MockResponse().setBody("""{"settings":{"units":"mmol","thresholds":{"bgTargetTop":180,"bgTargetBottom":72}}}""")
                req.path!!.startsWith("/api/v2/properties") -> MockResponse().setBody(fixture("properties_aaps.json"))
                else -> MockResponse().setResponseCode(404)
            }
        }
        val client = NightscoutClient(server.url("/").toString(), "reader-abc123")
        val s = client.fetchSnapshot()
        assertEquals(142.0, s.glucoseMgdl)
        assertEquals(GlucoseUnit.MMOL, s.serverUnits)
        assertEquals(TargetRange(72.0, 180.0), s.targetRange)
        val propsRequest = generateSequence { server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS) }
            .first { it.path!!.startsWith("/api/v2/") }
        assertEquals("reader-abc123", propsRequest.requestUrl!!.queryParameter("token"))
        assertEquals("GET", propsRequest.method)
    }

    @Test
    fun `falls back to entries when v2 is missing`() {
        respond { req ->
            if (req.path!!.startsWith("/api/v1/entries")) MockResponse().setBody(fixture("entries.json"))
            else MockResponse().setResponseCode(404)
        }
        val s = NightscoutClient(server.url("/").toString(), null).fetchSnapshot(GlucoseUnit.MGDL)
        assertEquals(182.0, s.glucoseMgdl)
        assertEquals(11.0, s.deltaMgdl)
    }

    @Test
    fun `reports a bad token clearly`() {
        respond { MockResponse().setResponseCode(401) }
        val e = assertFailsWith<NightscoutException> {
            NightscoutClient(server.url("/").toString(), "wrong").fetchSnapshot(GlucoseUnit.MGDL)
        }
        assertTrue(e.message!!.contains("token"))
    }

    @Test
    fun `normalizes pasted urls`() {
        assertEquals("https://my.ns.site/", NightscoutClient.normalizeUrl("my.ns.site/")!!.toString())
        assertEquals("https://my.ns.site/", NightscoutClient.normalizeUrl(" https://my.ns.site/?token=abc ")!!.toString())
        assertEquals("abc", NightscoutClient.tokenFromUrl("https://my.ns.site/?token=abc"))
        assertEquals(null, NightscoutClient.normalizeUrl("   "))
    }
}
