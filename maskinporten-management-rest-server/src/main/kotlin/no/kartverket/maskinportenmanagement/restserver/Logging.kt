package no.kartverket.maskinportenmanagement.restserver

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient.Companion.CLIENT_CERTIFICATE_HEADER
import org.slf4j.LoggerFactory

fun Application.configureLogging() {
    val accessLogEnabled = environment.config.boolean("accessLog.enabled", default = true)
    install(CallLogging) {
        // Workload: on every line logged during a call
        mdc("workload") { workloadFrom(it.request.headers[CLIENT_CERTIFICATE_HEADER]) }

        // Access log: one line per call on the "access" logger, if switched on
        logger = LoggerFactory.getLogger("access")
        disableDefaultColors()
        filter { accessLogEnabled }
    }
}

// The client cert header contains a list of caller IDs. The last one is the most accurate.
internal fun workloadFrom(clientCertificate: String?): String? =
    clientCertificate
        ?.split(',', ';')
        ?.map { it.trim() }
        ?.lastOrNull { it.startsWith("URI=spiffe://") }
        ?.removePrefix("URI=")
