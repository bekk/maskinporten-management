package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLDecodeException
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.di.DI
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.queryString
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.RoutingCall
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.auth.MaskinportenTokenProvider
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient.Companion.CLIENT_CERTIFICATE_HEADER
import no.kartverket.maskinportenmanagement.client.filtering.ScopeFilter
import no.kartverket.maskinportenmanagement.client.http.JavaOutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import no.kartverket.maskinportenmanagement.client.kms.CloudKms
import no.kartverket.maskinportenmanagement.client.kms.LocalKms
import java.io.File
import java.net.URI
import java.net.http.HttpClient
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
    val keyVersion = config.optional("kms.keyVersion")
    val localKeyFile = config.optional("kms.localKeyFile")
    check((keyVersion == null) != (localKeyFile == null)) {
        "Set exactly one of kms.keyVersion (Cloud KMS) and kms.localKeyFile (a local key) (see .env.example)"
    }
    // In SKIP, Workload Identity provides the credentials for Cloud KMS
    val kms = keyVersion?.let(::CloudKms) ?: LocalKms.fromPem(pemFile("kms.localKeyFile", localKeyFile!!))
    val tokenProvider = MaskinportenTokenProvider(
        wellKnownUrl = URI(config.url("maskinporten.wellKnownUrl")),
        clientId = config.required("maskinporten.clientId"),
        scopes = config.required("maskinporten.scopes"),
        kms = kms,
        certificateChainPem = pemFile("maskinporten.certificateChainFile", config.required("maskinporten.certificateChainFile")),
        httpClient = httpClient,
    )
    return MaskinportenManagementClient(config.url("digdir.baseUrl"), httpClient, tokenProvider)
}

private fun pemFile(setting: String, path: String): String {
    val file = File(path)
    check(file.isFile) { "$setting must point to a PEM file, but was \"$path\" (see .env.example)" }
    return file.readText()
}

// On unless turned off, so a missing URL stops the server instead of showing every app all of Kartverket's scopes
private fun Application.externalFilteringFromConfig(): ExternalFiltering {
    if (!environment.config.boolean("externalFiltering.enabled", default = true)) return ExternalFiltering(null)
    return ExternalFiltering(ExternalFilteringClient(environment.config.url("externalFiltering.url"), httpClient()))
}

private fun httpClient() = JavaOutgoingHttpClient(
    HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
    REQUEST_TIMEOUT,
)

class ExternalFiltering(private val client: ExternalFilteringClient?) {
    suspend fun filterFor(call: ApplicationCall): ScopeFilter? {
        val client = client ?: return null
        val certificate = call.request.headers[CLIENT_CERTIFICATE_HEADER]?.takeIf { it.isNotBlank() }
            ?: throw InvalidRequestException("Header $CLIENT_CERTIFICATE_HEADER is required")
        return client.filterFor(certificate)
    }
}

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)

internal class InvalidRequestException(message: String) : RuntimeException(message)

internal fun ApplicationCall.onlyQueryParameter(name: String): String = onlyQueryParameter(request.queryString(), name)

// Read from the raw query, so OPA cannot see another value: Netty drops parameters past the 1024th and reads "=scope=x"
// as scope, which OPA's parser does not
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

internal fun RoutingCall.organizationNumberPathParameter(name: String): String {
    val value = pathParameters[name].orEmpty()
    if (!ORGANIZATION_NUMBER.matches(value)) {
        throw InvalidRequestException("Path parameter $name must be an organisation number of 9 digits")
    }
    return value
}

private val ORGANIZATION_NUMBER = Regex("[0-9]{9}")

internal suspend fun ApplicationCall.respondFromDigdir(response: OutgoingResponse) {
    respondBytes(
        bytes = response.body,
        contentType = response.contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() },
        status = HttpStatusCode.fromValue(response.statusCode),
    )
}
