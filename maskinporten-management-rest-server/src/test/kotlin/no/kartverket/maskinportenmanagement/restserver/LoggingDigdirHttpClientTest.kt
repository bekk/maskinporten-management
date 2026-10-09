package no.kartverket.maskinportenmanagement.restserver

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.runBlocking
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringException
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpResponse
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LoggingDigdirHttpClientTest {

    private lateinit var appender: ListAppender<ILoggingEvent>

    private val outgoing get() = LoggerFactory.getLogger("outgoing") as Logger

    private val lines get() = appender.list.map { it.formattedMessage }

    @BeforeTest
    fun attachAppender() {
        appender = ListAppender<ILoggingEvent>().apply { start() }
        outgoing.addAppender(appender)
    }

    @AfterTest
    fun detachAppender() {
        outgoing.detachAppender(appender)
    }

    @Test
    fun `logs the call before it is sent and its status after, but not its headers`() = runBlocking {
        val client = LoggingDigdirHttpClient {
            assertEquals(listOf("Calling POST https://filtering.test/filter"), lines)
            DigdirHttpResponse(200, "application/json", """{"exact":[],"prefix":[]}""".toByteArray())
        }

        ExternalFilteringClient("https://filtering.test/filter", client).filterFor("URI=spiffe://cluster.local/ns/a/sa/b")

        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("POST https://filtering.test/filter answered 200 in "), lines[1])
        assertTrue(lines.none { "spiffe" in it }, "a header was logged: $lines")
    }

    @Test
    fun `logs a call that got no answer, and passes the failure on`() {
        val client = LoggingDigdirHttpClient { throw IOException("Connection refused") }

        assertFailsWith<ExternalFilteringException> {
            runBlocking { ExternalFilteringClient("https://filtering.test/filter", client).filterFor("certificate") }
        }

        assertTrue(lines.last().startsWith("POST https://filtering.test/filter failed after "), lines.last())
        assertContains(lines.last(), "Connection refused")
    }
}
