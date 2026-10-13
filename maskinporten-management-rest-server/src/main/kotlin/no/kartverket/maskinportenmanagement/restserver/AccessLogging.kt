package no.kartverket.maskinportenmanagement.restserver

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient.Companion.CLIENT_CERTIFICATE_HEADER
import org.slf4j.LoggerFactory

// Installed even with the access log off, since it also puts the calling app on every line logged during a call
fun Application.configureAccessLogging() {
    val enabled = environment.config.boolean("accessLog.enabled", default = true)
    install(CallLogging) {
        logger = LoggerFactory.getLogger("access")
        mdc("workload") { workloadFrom(it.request.headers[CLIENT_CERTIFICATE_HEADER]) }
        if (!enabled) filter { false }
    }
}

private val spiffeUri = Regex("""URI=spiffe://[^/;,"]+/ns/([^/;,"]+)/sa/([^/;,"]+)""")

// The last match, since Istio appends the caller it verified after any header the caller sent itself
internal fun workloadFrom(clientCertificate: String?): String? =
    clientCertificate?.let { spiffeUri.findAll(it).lastOrNull() }?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }
