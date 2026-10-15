package com.maskinporten.repositories.scopes

import com.maskinporten.dto.CreateScopeRequest
import com.maskinporten.dto.UpdateScopeRequest
import com.maskinporten.dto.toScope
import com.maskinporten.errors.ConflictException
import com.maskinporten.models.scopes.*
import io.ktor.server.plugins.NotFoundException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.toKotlinInstant

object ScopesTable : Table("scopes") {
    val name = varchar("name", 255)
    val prefix = varchar("prefix", 255)
    val subscope = varchar("subscope", 255)
    val description = text("description")
    val longDescription = text("long_description").nullable()
    val descriptions = text("descriptions").nullable()
    val active = bool("active")
    val created = timestampWithTimeZone("created")
    val lastUpdated = timestampWithTimeZone("last_updated")
    val allowedIntegrationTypes = array<String>("allowed_integration_types")
    val ownerOrgno = varchar("owner_orgno", 20)
    val tokenType = varchar("token_type", 50)
    val visibility = varchar("visibility", 50)
    val requiresUserConsent = bool("requires_user_consent")
    val requiresUserAuthentication = bool("requires_user_authentication")
    val requiresPseudonymousTokens = bool("requires_pseudonymous_tokens")
    val delegationSource = varchar("delegation_source", 255).nullable()
    val atMaxAge = long("at_max_age")
    val authorizationMaxLifetime = long("authorization_max_lifetime").nullable()
    val accessibleForAll = bool("accessible_for_all")
    val enforcedAudForAccessToken = varchar("enforced_aud_for_access_token", 255).nullable()
    val protected_ = bool("protected").nullable()
    val supportsEuropeanBusinesses = bool("supports_european_businesses").nullable()
    override val primaryKey = PrimaryKey(name)
}

fun ResultRow.toScope(): Scope {
    return Scope(
        name = this[ScopesTable.name],
        prefix = this[ScopesTable.prefix],
        subscope = this[ScopesTable.subscope],
        description = this[ScopesTable.description],
        long_description = this[ScopesTable.longDescription],
        descriptions = this[ScopesTable.descriptions]?.let { Json.decodeFromString<Map<String, String>>(it) } ?: emptyMap(),
        active = this[ScopesTable.active],
        created = this[ScopesTable.created].toInstant().toKotlinInstant(),
        last_updated = this[ScopesTable.lastUpdated].toInstant().toKotlinInstant(),
        allowed_integration_types = this[ScopesTable.allowedIntegrationTypes].map {
            IntegrationType.entries.first { e -> e.name.equals(it, ignoreCase = true) }
        },
        owner_orgno = this[ScopesTable.ownerOrgno],
        token_type = TokenType.valueOf(this[ScopesTable.tokenType]),
        visibility = Visibility.valueOf(this[ScopesTable.visibility]),
        requires_user_consent = this[ScopesTable.requiresUserConsent],
        requires_user_authentication = this[ScopesTable.requiresUserAuthentication],
        requires_pseudonymous_tokens = this[ScopesTable.requiresPseudonymousTokens],
        delegation_source = this[ScopesTable.delegationSource],
        at_max_age = this[ScopesTable.atMaxAge],
        authorization_max_lifetime = this[ScopesTable.authorizationMaxLifetime],
        accessible_for_all = this[ScopesTable.accessibleForAll],
        enforced_aud_for_access_token = this[ScopesTable.enforcedAudForAccessToken],
        protected = this[ScopesTable.protected_],
        supports_european_businesses = this[ScopesTable.supportsEuropeanBusinesses]
    )
}

class PostgresScopesRepository : ScopesRepository {

    override fun getAllScopes(
        prefix: String?,
        includeInactive: Boolean,
        onlyWithDelegationSource: Boolean,
        onlyAccessibleForAll: Boolean,
        integrationType: IntegrationType?
    ): List<Scope> {
        return transaction {
            ScopesTable.selectAll()
                .apply {
                    prefix?.let { andWhere { ScopesTable.prefix eq it } }
                    if (!includeInactive) andWhere { ScopesTable.active eq true }
                    if (onlyWithDelegationSource) andWhere { ScopesTable.delegationSource.isNotNull() }
                    if (onlyAccessibleForAll) andWhere { ScopesTable.accessibleForAll eq true }
                }
                .map { it.toScope() }
                .filter { integrationType == null || integrationType in it.allowed_integration_types }
        }
    }

    override fun getScopes(inactive: Boolean): List<Scope> {
        return transaction {
            ScopesTable.selectAll()
                .apply { if (!inactive) andWhere { ScopesTable.active eq true } }
                .map { it.toScope() }
        }
    }

    override fun getScopeByName(name: String): Scope {
        return transaction {
            ScopesTable.selectAll()
                .where { ScopesTable.name eq name }
                .firstOrNull()
                ?.toScope()
                ?: throw NotFoundException("Scope not found: $name")
        }
    }

    override fun createScope(scope: CreateScopeRequest): Scope {
        val key = "${scope.prefix}:${scope.subscope}"
        return transaction {
            val existing = ScopesTable.selectAll()
                .where { ScopesTable.name eq key }
                .firstOrNull()
            if (existing != null) {
                throw ConflictException("Scope already exists: $key")
            }

            val newScope = scope.toScope()
            val now = java.time.OffsetDateTime.now()

            ScopesTable.insert {
                it[ScopesTable.name] = newScope.name
                it[prefix] = newScope.prefix
                it[subscope] = newScope.subscope
                it[description] = newScope.description
                it[longDescription] = newScope.long_description
                it[active] = newScope.active
                it[created] = now
                it[lastUpdated] = now
                it[allowedIntegrationTypes] = newScope.allowed_integration_types.map { t -> t.name.lowercase() }
                it[ownerOrgno] = newScope.owner_orgno
                it[tokenType] = newScope.token_type.name
                it[visibility] = newScope.visibility.name
                it[requiresUserConsent] = newScope.requires_user_consent
                it[requiresUserAuthentication] = newScope.requires_user_authentication
                it[requiresPseudonymousTokens] = newScope.requires_pseudonymous_tokens
                it[delegationSource] = newScope.delegation_source
                it[atMaxAge] = newScope.at_max_age
                it[authorizationMaxLifetime] = newScope.authorization_max_lifetime
                it[accessibleForAll] = newScope.accessible_for_all
                it[enforcedAudForAccessToken] = newScope.enforced_aud_for_access_token
                it[protected_] = newScope.protected
                it[supportsEuropeanBusinesses] = newScope.supports_european_businesses
            }
            newScope
        }
    }

    override fun deleteScope(name: String): Scope {
        val scopeName = name
        return transaction {
            val existing = getScopeByName(scopeName)
            ScopesTable.deleteWhere { Op.build { ScopesTable.name eq scopeName } }
            existing
        }
    }

    override fun updateScope(name: String, scope: UpdateScopeRequest): Scope {
        return transaction {
            val existing = ScopesTable.selectAll()
                .where { ScopesTable.name eq name }
                .firstOrNull()
                ?.toScope()
                ?: throw NotFoundException("Scope not found: $name")

            val newPrefix = scope.prefix ?: existing.prefix
            val newSubscope = scope.subscope ?: existing.subscope
            val newName = "$newPrefix:$newSubscope"
            if (newName != name && ScopesTable.selectAll().where { ScopesTable.name eq newName }.any()) {
                throw ConflictException("Scope already exists: $newName")
            }

            val now = java.time.OffsetDateTime.now()

            ScopesTable.update({ ScopesTable.name eq name }) {
                it[ScopesTable.name] = newName
                it[prefix] = newPrefix
                it[subscope] = newSubscope
                it[description] = scope.description ?: existing.description
                it[longDescription] = scope.long_description ?: existing.long_description
                it[active] = scope.active ?: existing.active
                it[ownerOrgno] = scope.owner_orgno ?: existing.owner_orgno
                it[allowedIntegrationTypes] = (scope.allowed_integration_types ?: existing.allowed_integration_types).map { t -> t.name.lowercase() }
                it[tokenType] = (scope.token_type ?: existing.token_type).name
                it[visibility] = (scope.visibility ?: existing.visibility).name
                it[delegationSource] = scope.delegation_source ?: existing.delegation_source
                it[atMaxAge] = scope.at_max_age ?: existing.at_max_age
                it[authorizationMaxLifetime] = scope.authorization_max_lifetime ?: existing.authorization_max_lifetime
                it[requiresUserAuthentication] = scope.requires_user_authentication ?: existing.requires_user_authentication
                it[requiresUserConsent] = scope.requires_user_consent ?: existing.requires_user_consent
                it[requiresPseudonymousTokens] = scope.requires_pseudonymous_tokens ?: existing.requires_pseudonymous_tokens
                it[accessibleForAll] = scope.accessible_for_all ?: existing.accessible_for_all
                it[enforcedAudForAccessToken] = scope.enforced_aud_for_access_token ?: existing.enforced_aud_for_access_token
                it[protected_] = scope.protected ?: existing.protected
                it[supportsEuropeanBusinesses] = scope.supports_european_businesses ?: existing.supports_european_businesses
                it[lastUpdated] = now
            }

            ScopesTable.selectAll()
                .where { ScopesTable.name eq newName }
                .first()
                .toScope()
        }
    }
}
