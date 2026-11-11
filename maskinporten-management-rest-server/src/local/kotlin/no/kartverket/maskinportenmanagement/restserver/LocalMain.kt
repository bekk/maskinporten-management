package no.kartverket.maskinportenmanagement.restserver

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.auth.AccessToken
import no.kartverket.maskinportenmanagement.restserver.config.AppConfig
import no.kartverket.maskinportenmanagement.restserver.config.externalFiltering
import no.kartverket.maskinportenmanagement.restserver.config.file
import no.kartverket.maskinportenmanagement.restserver.config.loadApplicationConfig
import no.kartverket.maskinportenmanagement.restserver.http.LoggingOutgoingHttpClient
import java.net.URI

// main(), but with fakes for Digdir and the filtering service, since nothing plays them locally. The fake Digdir takes
// any token, so a local run needs no Digdir settings
fun main() {
    val applicationConfig = loadApplicationConfig()
    applicationConfig.file("logback.configFile")?.let { System.setProperty("logback.configurationFile", it) }
    val config = AppConfig.load(applicationConfig)
    val digdir = LoggingOutgoingHttpClient(FakeDigdir())

    embeddedServer(Netty, port = config.port) {
        module(
            client = MaskinportenManagementClient(URI("http://digdir.local"), digdir) { AccessToken.of("local") },
            externalFiltering = externalFiltering(config, LoggingOutgoingHttpClient(fakeFilteringService)),
            accessLog = config.accessLog,
        )
    }.start(wait = true)
}
