package com.maskinporten.maskinporten

import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.slf4j.Logger
import java.io.File
import java.security.SecureRandom
import java.security.Signature
import java.security.SignatureException
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.naming.ldap.LdapName
import javax.security.auth.x500.X500Principal

private class InvalidGrantException(message: String) : RuntimeException(message)

/** What a token was issued for: the organisation in the business certificate, and the scopes the grant asked for. */
data class IssuedToken(val token: String, val organizationNumber: String, val scope: String, val expiresAt: Instant)

/**
 * Plays Maskinporten for the JWT grants maskinporten-management sends: it trusts business certificates from [trustedCa],
 * checks the grant like Maskinporten does, and hands out tokens it remembers the organisation and scopes of.
 */
class MaskinportenMock(issuer: String, private val trustedCa: X509Certificate) {
    /** Ends with `/`, like Maskinporten's `https://maskinporten.no/`, so the token endpoint is `${issuer}token`. */
    val issuer = issuer.removeSuffix("/") + "/"
    private val tokens = ConcurrentHashMap<String, IssuedToken>()
    // jti of each grant used (or the whole grant, when it has no jti), until the grant expires, so no grant is used twice
    private val usedGrants = ConcurrentHashMap<String, Instant>()
    private val random = SecureRandom()

    /** The token's organisation and scopes, or null when the token is unknown or has expired. */
    fun issuedToken(token: String): IssuedToken? = tokens[token]?.takeIf { it.expiresAt > Instant.now() }

    fun issueToken(grantType: String?, assertion: String?): IssuedToken {
        if (grantType != GRANT_TYPE) invalidGrant("grant_type must be $GRANT_TYPE")
        val parts = assertion?.split('.')?.takeIf { it.size == 3 } ?: invalidGrant("assertion must be a signed JWT")
        val header = decodeJson(parts[0])
        val claims = decodeJson(parts[1])

        val chain = header["x5c"]?.jsonArray?.map { certificate(it.jsonPrimitive.content) }?.takeIf { it.isNotEmpty() }
            ?: invalidGrant("x5c must hold the business certificate chain")
        validateChain(chain)
        val algorithm = header["alg"]?.jsonPrimitive?.content
        val signatureAlgorithm = SIGNATURE_ALGORITHMS[algorithm] ?: invalidGrant("alg must be one of ${SIGNATURE_ALGORITHMS.keys}")
        // A signature that is not base64url, or has the wrong length, throws instead of failing to verify
        val signatureIsValid = try {
            Signature.getInstance(signatureAlgorithm).run {
                initVerify(chain.first().publicKey)
                update("${parts[0]}.${parts[1]}".toByteArray())
                verify(Base64.getUrlDecoder().decode(parts[2]))
            }
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: SignatureException) {
            false
        }
        if (!signatureIsValid) invalidGrant("The signature does not match the business certificate")
        if (claims["aud"]?.jsonPrimitive?.content != issuer) invalidGrant("aud must be $issuer")
        if (claims["iss"]?.jsonPrimitive?.content.isNullOrBlank()) invalidGrant("iss is required")
        val now = Instant.now()
        val issuedAt = claims["iat"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochSecond) ?: invalidGrant("iat is required")
        if (Duration.between(issuedAt, now).abs() > MAX_CLOCK_SKEW) invalidGrant("iat must be within $MAX_CLOCK_SKEW of now")
        val expiresAt = claims["exp"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochSecond) ?: invalidGrant("exp is required")
        if (expiresAt < now) invalidGrant("The grant has expired")
        if (Duration.between(issuedAt, expiresAt) > MAX_GRANT_LIFETIME) invalidGrant("exp can be at most $MAX_GRANT_LIFETIME after iat")
        val scope = claims["scope"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: invalidGrant("scope is required")
        val organizationNumber = organizationNumber(chain.first())
        // Last, so a grant refused for another reason is not used up
        val grantId = claims["jti"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: assertion
        usedGrants.values.removeIf { it < now }
        if (usedGrants.putIfAbsent(grantId, expiresAt) != null) invalidGrant("The grant has been used before")

        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        tokens.values.removeIf { it.expiresAt < now }
        return IssuedToken(token, organizationNumber, scope, now.plusSeconds(TOKEN_LIFETIME_SECONDS)).also { tokens[token] = it }
    }

    private fun validateChain(chain: List<X509Certificate>) {
        // The CA is the trust anchor, so it must not be part of the path itself
        val path = CertificateFactory.getInstance("X.509").generateCertPath(chain.filter { it != trustedCa })
        val parameters = PKIXParameters(setOf(TrustAnchor(trustedCa, null))).apply { isRevocationEnabled = false }
        try {
            CertPathValidator.getInstance("PKIX").validate(path, parameters)
        } catch (e: Exception) {
            invalidGrant("The business certificate is not from the trusted CA: ${e.message}")
        }
    }

    companion object {
        const val TOKEN_LIFETIME_SECONDS = 120L
        private const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        private val SIGNATURE_ALGORITHMS = mapOf("RS256" to "SHA256withRSA", "RS384" to "SHA384withRSA", "RS512" to "SHA512withRSA")
        // Maskinporten's limits, from https://docs.digdir.no/docs/Maskinporten/maskinporten_protocol_jwtgrant
        private val MAX_CLOCK_SKEW = Duration.ofSeconds(10)
        private val MAX_GRANT_LIFETIME = Duration.ofSeconds(180)
        private const val SERIAL_NUMBER = "2.5.4.5"
        private const val ORGANIZATION_IDENTIFIER = "2.5.4.97"

        /**
         * Newer business certificates have the organisation number in organizationIdentifier as `NTRNO-971040238`, older
         * ones in serialNumber. Java prints both as hex codes (`2.5.4.5=#13…`) unless it is given names for them.
         */
        private fun organizationNumber(certificate: X509Certificate): String {
            val names = mapOf(SERIAL_NUMBER to "SERIALNUMBER", ORGANIZATION_IDENTIFIER to "ORGANIZATIONIDENTIFIER")
            val subject = LdapName(certificate.subjectX500Principal.getName(X500Principal.RFC2253, names)).rdns
                .associate { it.type.uppercase() to it.value.toString() }
            return subject["ORGANIZATIONIDENTIFIER"]?.removePrefix("NTRNO-")?.takeIf(::isOrganizationNumber)
                ?: subject["SERIALNUMBER"]?.takeIf(::isOrganizationNumber)
                ?: invalidGrant("The business certificate has no organisation number")
        }

        private fun isOrganizationNumber(value: String) = value.length == 9 && value.all(Char::isDigit)

        private fun invalidGrant(message: String): Nothing = throw InvalidGrantException(message)

        private fun decodeJson(part: String) = try {
            Json.parseToJsonElement(Base64.getUrlDecoder().decode(part).decodeToString()).jsonObject
        } catch (e: IllegalArgumentException) {
            invalidGrant("The JWT is not base64url-encoded JSON")
        }

        private fun certificate(base64Der: String): X509Certificate = try {
            CertificateFactory.getInstance("X.509").generateCertificate(Base64.getDecoder().decode(base64Der).inputStream()) as X509Certificate
        } catch (e: Exception) {
            invalidGrant("x5c must hold base64-encoded DER certificates")
        }
    }
}

/**
 * Null, so there is no /token, when MASKINPORTEN_ENABLED is false, or when it is not set and there is no CA certificate:
 * then the mock runs without scripts/setup-local-kms.sh for those who do not need /token.
 */
fun maskinportenMockFromConfig(config: ApplicationConfig, log: Logger): MaskinportenMock? {
    fun setting(path: String, env: String) = config.propertyOrNull(path)?.getString() ?: System.getenv(env)
    fun setting(path: String, env: String, default: String) = setting(path, env) ?: default

    val enabled = setting("maskinporten.enabled", "MASKINPORTEN_ENABLED")?.toBoolean()
    if (enabled == false) return null
    val caFile = File(setting("maskinporten.caFile", "MASKINPORTEN_CA_FILE", "local-kms/ca.crt"))
    if (!caFile.isFile) {
        check(enabled == null) { "No CA certificate at ${caFile.absolutePath}; run scripts/setup-local-kms.sh" }
        log.warn("No CA certificate at ${caFile.absolutePath}, so there is no /token. Run scripts/setup-local-kms.sh to get it.")
        return null
    }
    return MaskinportenMock(
        issuer = setting("maskinporten.issuer", "MASKINPORTEN_ISSUER", "http://localhost:8090/"),
        trustedCa = caFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate },
    )
}

fun Route.maskinportenRoutes(maskinporten: MaskinportenMock) {
    get("/.well-known/oauth-authorization-server") {
        call.respond(
            buildJsonObject {
                put("issuer", maskinporten.issuer)
                put("token_endpoint", "${maskinporten.issuer}token")
            },
        )
    }

    post("/token") {
        val parameters = call.receiveParameters()
        try {
            val issued = maskinporten.issueToken(parameters["grant_type"], parameters["assertion"])
            call.respond(
                buildJsonObject {
                    put("access_token", issued.token)
                    put("token_type", "Bearer")
                    put("expires_in", MaskinportenMock.TOKEN_LIFETIME_SECONDS)
                    put("scope", issued.scope)
                },
            )
        } catch (e: InvalidGrantException) {
            call.application.environment.log.warn("Refused a token request: ${e.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                buildJsonObject {
                    put("error", "invalid_grant")
                    put("error_description", e.message)
                },
            )
        }
    }
}
