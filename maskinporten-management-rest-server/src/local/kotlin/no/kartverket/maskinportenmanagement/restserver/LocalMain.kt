package no.kartverket.maskinportenmanagement.restserver

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.kartverket.maskinportenmanagement.restserver.config.AppConfig
import no.kartverket.maskinportenmanagement.restserver.config.externalFiltering
import no.kartverket.maskinportenmanagement.restserver.config.file
import no.kartverket.maskinportenmanagement.restserver.config.loadApplicationConfig
import no.kartverket.maskinportenmanagement.restserver.config.maskinportenManagementClient

// main(), but with the fake filtering service, since nothing plays the real one locally
fun main() {
    val applicationConfig = loadApplicationConfig()
    applicationConfig.file("logback.configFile")?.let { System.setProperty("logback.configurationFile", it) }
    val config = AppConfig.load(applicationConfig)

    embeddedServer(Netty, port = config.port) {
        module(
            client = maskinportenManagementClient(config),
            externalFiltering = externalFiltering(config, fakeFilteringService),
            accessLog = config.accessLog,
        )
    }.start(wait = true)
}
