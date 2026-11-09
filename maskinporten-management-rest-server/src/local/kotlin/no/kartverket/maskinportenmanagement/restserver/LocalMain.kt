package no.kartverket.maskinportenmanagement.restserver

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.auth.AccessToken
import no.kartverket.maskinportenmanagement.restserver.config.boolean
import no.kartverket.maskinportenmanagement.restserver.config.file
import no.kartverket.maskinportenmanagement.restserver.config.loadApplicationConfig
import no.kartverket.maskinportenmanagement.restserver.config.port
import no.kartverket.maskinportenmanagement.restserver.filtering.ExternalFiltering
import no.kartverket.maskinportenmanagement.restserver.filtering.ExternalFilteringClient
import java.net.URI

// main(), but with fakes for Digdir and the filtering service, since nothing plays them locally. The fake Digdir takes
// any token, so a local run reads only the server's own settings
fun main() {
    val config = loadApplicationConfig()
    config.file("logback.configFile")?.let { System.setProperty("logback.configurationFile", it) }
    val filtering = if (config.boolean("externalFiltering.enabled", default = false)) {
        ExternalFiltering(ExternalFilteringClient(URI("http://filtering.local"), fakeFilteringService))
    } else {
        ExternalFiltering(null)
    }

    embeddedServer(Netty, port = config.port("server.port")) {
        module(
            client = MaskinportenManagementClient(URI("http://digdir.local"), FakeDigdir()) { AccessToken.of("local") },
            externalFiltering = filtering,
            accessLog = config.boolean("accessLog.enabled", default = true),
        )
    }.start(wait = true)
}
