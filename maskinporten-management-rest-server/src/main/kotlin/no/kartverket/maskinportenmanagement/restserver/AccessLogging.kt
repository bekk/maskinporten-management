package no.kartverket.maskinportenmanagement.restserver

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import org.slf4j.LoggerFactory

fun Application.configureAccessLogging() {
    if (!environment.config.boolean("accessLog.enabled", default = true)) return
    install(CallLogging) {
        logger = LoggerFactory.getLogger("access")
    }
}

private val spiffeUri = Regex("""URI=spiffe://[^/;,"]+/ns/([^/;,"]+)/sa/([^/;,"]+)""")

// The last match, since Istio appends the caller it verified after any header the caller sent itself
internal fun workloadFrom(clientCertificate: String?): String? =
    clientCertificate?.let { spiffeUri.findAll(it).lastOrNull() }?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }
