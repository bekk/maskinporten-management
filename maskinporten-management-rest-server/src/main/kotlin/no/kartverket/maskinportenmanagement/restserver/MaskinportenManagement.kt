package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.URLDecodeException
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.request.contentType
import io.ktor.server.request.queryString
import io.ktor.server.request.receive
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
import java.net.http.HttpClient
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.time.Clock
import java.time.Duration

internal fun Application.maskinportenManagementClient(config: AppConfig): MaskinportenManagementClient {
    val httpClient = httpClient()
    val kms = config.digdirAdminCloudKmsKeyPath?.let(::CloudKms) ?: LocalKms.fromPem(checkNotNull(config.digdirAdminLocalKey))
    val tokenProvider = MaskinportenTokenProvider(
        wellKnownUrl = config.digdirEnvironment.maskinportenWellKnownUrl,
        clientId = config.maskinportenClientId,
        scopes = DIGDIR_SCOPES,
        kms = kms,
        certificateChainPem = config.digdirAdminCertChain,
        httpClient = httpClient,
    )
    monitor.subscribe(ApplicationStopped) {
        tokenProvider.close()
        (kms as? AutoCloseable)?.close()
    }
    return MaskinportenManagementClient(config.digdirEnvironment.digdirBaseUrl, httpClient, tokenProvider)
}

// Digdir's API requires idporten:scopes.read to read scope access and idporten:scopes.write to change it
private val DIGDIR_SCOPES = listOf(Scope.of("idporten:scopes.read"), Scope.of("idporten:scopes.write"))

internal fun Application.externalFiltering(config: AppConfig): ExternalFiltering {
    val url = config.externalFilteringUrl
    if (url == null) {
        log.warn("External filtering is off")
        return ExternalFiltering(null)
    }
    return ExternalFiltering(ExternalFilteringClient(url, httpClient()), config.externalFilteringCacheDuration)
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

internal fun onlyQueryParameter(query: String, name: String): String = queryParameters(query, name).getValue(name)

// Read from the raw query, so we act on the same values as the access check in front of the server: Netty drops
// parameters past the 1024th and reads "=scope=x" as scope, which other parsers do not
internal fun queryParameters(query: String, required: String, vararg optional: String): Map<String, String> {
    val names = listOf(required) + optional
    val onlyThese = if (optional.isEmpty()) {
        "The query must contain only the parameter $required, sent once"
    } else {
        "The query must contain only the parameters ${names.dropLast(1).joinToString()} and ${names.last()}, each sent once"
    }
    val parameters = if (query.isEmpty()) emptyList() else query.split('&')
    val raw = parameters.map { parameter ->
        val (name, value) = parameter.split('=', limit = 2).takeIf { it.size == 2 && it[0] in names }
            ?: throw InvalidRequestException(onlyThese)
        name to value
    }
    if (raw.distinctBy { it.first }.size != raw.size) throw InvalidRequestException(onlyThese)
    val values = raw.associate { (name, value) ->
        name to try {
            value.decodeURLQueryComponent(plusIsSpace = true)
        } catch (e: URLDecodeException) {
            throw InvalidRequestException("Query parameter $name is not URL-encoded correctly")
        }
    }
    if (values[required].isNullOrBlank()) throw InvalidRequestException("Query parameter $required is required")
    return values
}

// A query the server ignores could still be read by the access check in front of us, as a different scope
internal fun ApplicationCall.noQuery() {
    if (request.queryString().isNotEmpty()) throw InvalidRequestException("The query must be empty")
}

internal fun ApplicationCall.scopeQueryParameter(name: String): Scope = scopeParameter(name, onlyQueryParameter(name))

internal fun scopeParameter(name: String, value: String): Scope = runCatching { Scope.of(value) }.getOrNull()
    ?: throw InvalidRequestException("Query parameter $name must not contain whitespace")

internal fun wholeNumberParameter(name: String, value: String, min: Int): Int = value.toIntOrNull()?.takeIf { it >= min }
    ?: throw InvalidRequestException("Query parameter $name must be a whole number of $min or more")

// Strictly UTF-8, whatever charset the Content-Type names, so Digdir gets the bytes the access check in front of us saw
internal suspend fun ApplicationCall.jsonBody(): String {
    requireJsonContentType()
    return try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(receive<ByteArray>())).toString()
    } catch (e: CharacterCodingException) {
        throw InvalidRequestException("The body must be UTF-8")
    }
}

internal suspend fun ApplicationCall.scopeJsonBody(): ScopeJson = try {
    ScopeJson.of(jsonBody())
} catch (e: IllegalArgumentException) {
    throw InvalidRequestException(e.message!!)
}

private fun ApplicationCall.requireJsonContentType() {
    val contentType = try {
        request.contentType()
    } catch (e: BadContentTypeFormatException) {
        null
    }
    if (contentType?.withoutParameters() != ContentType.Application.Json) {
        throw InvalidRequestException("The body must be JSON, sent with Content-Type application/json")
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
