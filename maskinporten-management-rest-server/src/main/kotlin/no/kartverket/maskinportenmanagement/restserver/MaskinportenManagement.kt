package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.URLDecodeException
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.di.DI
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.contentType
import io.ktor.server.request.queryString
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.routing.RoutingCall
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.OrganizationNumber
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.client.ScopeJson
import no.kartverket.maskinportenmanagement.client.auth.MaskinportenTokenProvider
import no.kartverket.maskinportenmanagement.client.filtering.ClientCertificate
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient.Companion.CLIENT_CERTIFICATE_HEADER
import no.kartverket.maskinportenmanagement.client.filtering.ScopeFilter
import no.kartverket.maskinportenmanagement.client.http.JavaOutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.kms.LocalKms
import java.io.File
import java.net.http.HttpClient
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.time.Clock
import java.time.Duration

fun Application.configureMaskinportenManagement(
    client: MaskinportenManagementClient = clientFromConfig(),
    externalFiltering: ExternalFiltering = externalFilteringFromConfig(),
) {
    install(DI)
    dependencies.provide<MaskinportenManagementClient> { client }
    dependencies.provide<ExternalFiltering> { externalFiltering }
}

private fun Application.clientFromConfig(): MaskinportenManagementClient {
    val config = environment.config
    val httpClient = httpClient()
    val cloudKeyPath = config.optional("digdirAdmin.gcpCloudKmsKeyPath")
    val localKeyPath = config.optional("digdirAdmin.localKeyPath")
    check((cloudKeyPath == null) != (localKeyPath == null)) {
        "Set exactly one of digdirAdmin.gcpCloudKmsKeyPath (Cloud KMS) and digdirAdmin.localKeyPath (a local key) (see .env.example)"
    }
    val kms = cloudKeyPath?.let(::CloudKms) ?: LocalKms.fromPem(pemFile("digdirAdmin.localKeyPath", localKeyPath!!))
    val certChain = pemCertificateChain("digdirAdmin.certChain", config.required("digdirAdmin.certChain"))
    val digdirEnvironment = config.enum<DigdirEnvironment>("digdir.environment")
    val tokenProvider = MaskinportenTokenProvider(
        wellKnownUrl = digdirEnvironment.maskinportenWellKnownUrl,
        clientId = config.required("maskinporten.clientId"),
        scopes = DIGDIR_SCOPES,
        kms = kms,
        certificateChainPem = certChain,
        httpClient = httpClient,
    )
    monitor.subscribe(ApplicationStopped) {
        tokenProvider.close()
        (kms as? AutoCloseable)?.close()
    }
    return MaskinportenManagementClient(digdirEnvironment.digdirBaseUrl, httpClient, tokenProvider)
}

// Digdir's API requires idporten:scopes.read to read scope access and idporten:scopes.write to change it
private val DIGDIR_SCOPES = listOf(Scope.of("idporten:scopes.read"), Scope.of("idporten:scopes.write"))

private fun pemFile(setting: String, path: String): String {
    val file = File(path)
    check(file.isFile) { "$setting must point to a PEM file, but was \"$path\" (see .env.example)" }
    return file.readText()
}

// The client parses the chain too, but its errors cannot name the setting
private fun pemCertificateChain(setting: String, pem: String): String {
    val error = "$setting must be the certificate chain itself in PEM, but was \"$pem\" (see .env.example)"
    val certificates = try {
        CertificateFactory.getInstance("X.509").generateCertificates(pem.byteInputStream())
    } catch (e: CertificateException) {
        throw IllegalStateException(error, e)
    }
    check(certificates.isNotEmpty()) { error }
    return pem
}

private fun Application.externalFilteringFromConfig(): ExternalFiltering {
    val config = environment.config
    if (!config.boolean("externalFiltering.enabled", default = false)) {
        log.warn("External filtering is off")
        return ExternalFiltering(null)
    }
    return ExternalFiltering(
        ExternalFilteringClient(config.url("externalFiltering.url"), httpClient()),
        cacheDuration = config.seconds("externalFiltering.cacheSeconds", default = Duration.ofMinutes(1)),
    )
}

private fun httpClient(): OutgoingHttpClient = LoggingOutgoingHttpClient(
    JavaOutgoingHttpClient(
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(),
        REQUEST_TIMEOUT,
    ),
)

class ExternalFiltering(
    private val client: ExternalFilteringClient?,
    cacheDuration: Duration = Duration.ZERO,
    clock: Clock = Clock.systemUTC(),
) {
    private val cache = ScopeFilterCache(cacheDuration, clock)

    suspend fun filterFor(call: ApplicationCall): ScopeFilter? {
        val client = client ?: return null
        val certificate = call.request.headers[CLIENT_CERTIFICATE_HEADER]?.takeIf { it.isNotBlank() }
            ?: throw InvalidRequestException("Header $CLIENT_CERTIFICATE_HEADER is required")
        cache.get(certificate)?.let { return it }
        return client.filterFor(ClientCertificate.of(certificate)).also { cache.put(certificate, it) }
    }
}

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)

internal class InvalidRequestException(message: String) : RuntimeException(message)

internal fun ApplicationCall.onlyQueryParameter(name: String): String = onlyQueryParameter(request.queryString(), name)

// Read from the raw query, so we act on the same value as the access check in front of the server: Netty drops
// parameters past the 1024th and reads "=scope=x" as scope, which other parsers do not
internal fun onlyQueryParameter(query: String, name: String): String {
    if (query.isEmpty()) throw InvalidRequestException("Query parameter $name is required")
    val (key, raw) = query.split('=', limit = 2).takeIf { it.size == 2 && '&' !in query }
        ?: throw InvalidRequestException("The query must contain only the parameter $name, sent once")
    if (key != name) throw InvalidRequestException("The query must contain only the parameter $name, sent once")
    val value = try {
        raw.decodeURLQueryComponent(plusIsSpace = true)
    } catch (e: URLDecodeException) {
        throw InvalidRequestException("Query parameter $name is not URL-encoded correctly")
    }
    return value.takeIf { it.isNotBlank() } ?: throw InvalidRequestException("Query parameter $name is required")
}

// A query the server ignores could still be read by the access check in front of us, as a different scope
internal fun ApplicationCall.noQuery() {
    if (request.queryString().isNotEmpty()) throw InvalidRequestException("The query must be empty")
}

internal fun ApplicationCall.scopeQueryParameter(name: String): Scope {
    val value = onlyQueryParameter(name)
    return runCatching { Scope.of(value) }.getOrNull()
        ?: throw InvalidRequestException("Query parameter $name must not contain whitespace")
}

internal suspend fun ApplicationCall.jsonBody(): String {
    if (request.contentType().withoutParameters() != ContentType.Application.Json) {
        throw InvalidRequestException("The body must be JSON, sent with Content-Type application/json")
    }
    return receiveText()
}

// Decoded strictly as UTF-8, so the bytes sent on to Digdir are the ones the access check in front of us saw
internal suspend fun ApplicationCall.scopeJsonBody(): ScopeJson {
    if (request.contentType().withoutParameters() != ContentType.Application.Json) {
        throw InvalidRequestException("The body must be JSON, sent with Content-Type application/json")
    }
    val text = try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(receive<ByteArray>())).toString()
    } catch (e: CharacterCodingException) {
        throw InvalidRequestException("The body must be UTF-8")
    }
    return try {
        ScopeJson.of(text)
    } catch (e: IllegalArgumentException) {
        throw InvalidRequestException(e.message!!)
    }
}

internal fun RoutingCall.organizationNumberPathParameter(name: String): OrganizationNumber {
    val value = pathParameters[name].orEmpty()
    return runCatching { OrganizationNumber.of(value) }.getOrNull()
        ?: throw InvalidRequestException("Path parameter $name must be an organisation number of 9 digits")
}

internal fun ApplicationCall.organizationNumberQueryParameter(name: String): OrganizationNumber {
    val value = onlyQueryParameter(name)
    return runCatching { OrganizationNumber.of(value) }.getOrNull()
        ?: throw InvalidRequestException("Query parameter $name must be an organisation number of 9 digits")
}
