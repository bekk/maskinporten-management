package com.maskinporten.repositories.scopes.access

import com.maskinporten.models.scopes.Scope
import com.maskinporten.models.scopes.access.Access
import com.maskinporten.models.scopes.access.State
import com.maskinporten.repositories.organizations.OrganizationsTable
import io.ktor.server.plugins.NotFoundException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.toKotlinInstant

object ScopeAccessTable : Table("scope_access") {
    val scope = varchar("scope", 255)
    val ownerOrgno = varchar("owner_orgno", 20)
    val ownerOrganizationName = varchar("owner_organization_name", 255).nullable()
    val consumerOrgno = varchar("consumer_orgno", 20)
    val consumerOrganizationName = varchar("consumer_organization_name", 255).nullable()
    val state = varchar("state", 20)
    val active = bool("active")
    val created = timestampWithTimeZone("created")
    val lastUpdated = timestampWithTimeZone("last_updated")
    override val primaryKey = PrimaryKey(scope, consumerOrgno)
}

private fun ResultRow.toAccess(): Access {
    return Access(
        scope = this[ScopeAccessTable.scope],
        owner_orgno = this[ScopeAccessTable.ownerOrgno],
        owner_organization_name = this[ScopeAccessTable.ownerOrganizationName],
        consumer_orgno = this[ScopeAccessTable.consumerOrgno],
        consumer_organization_name = this[ScopeAccessTable.consumerOrganizationName],
        state = State.valueOf(this[ScopeAccessTable.state]),
        created = this[ScopeAccessTable.created].toInstant().toKotlinInstant(),
        last_updated = this[ScopeAccessTable.lastUpdated].toInstant().toKotlinInstant()
    )
}

private fun organizationName(orgno: String): String? =
    OrganizationsTable.selectAll()
        .where { OrganizationsTable.orgnr eq orgno }
        .firstOrNull()
        ?.get(OrganizationsTable.name)

class PostgresAccessRepository : AccessRepository {

    override fun getAllScopesAccess(includeInactive: Boolean): List<Access> {
        return transaction {
            ScopeAccessTable.selectAll()
                .apply { if (!includeInactive) andWhere { ScopeAccessTable.active eq true } }
                .map { it.toAccess() }
        }
    }

    override fun getScopesAccess(consumerOrgNo: String?, scope: String?, includeInactive: Boolean): List<Access> {
        return transaction {
            ScopeAccessTable.selectAll()
                .apply {
                    consumerOrgNo?.let { andWhere { ScopeAccessTable.consumerOrgno eq it } }
                    scope?.let { andWhere { ScopeAccessTable.scope eq it } }
                    if (!includeInactive) andWhere { ScopeAccessTable.active eq true }
                }
                .map { it.toAccess() }
        }
    }

    override fun updateScopeAccess(consumerOrgNo: String, scope: Scope): Access {
        return transaction {
            val now = java.time.OffsetDateTime.now()

            // Upsert: insert or update
            val existing = ScopeAccessTable.selectAll()
                .where { (ScopeAccessTable.scope eq scope.name) and (ScopeAccessTable.consumerOrgno eq consumerOrgNo) }
                .firstOrNull()

            if (existing != null) {
                ScopeAccessTable.update({
                    (ScopeAccessTable.scope eq scope.name) and (ScopeAccessTable.consumerOrgno eq consumerOrgNo)
                }) {
                    it[state] = State.APPROVED.name
                    it[active] = true
                    it[lastUpdated] = now
                }
            } else {
                ScopeAccessTable.insert {
                    it[ScopeAccessTable.scope] = scope.name
                    it[ownerOrgno] = scope.owner_orgno
                    it[ownerOrganizationName] = organizationName(scope.owner_orgno)
                    it[consumerOrgno] = consumerOrgNo
                    it[consumerOrganizationName] = organizationName(consumerOrgNo)
                    it[state] = State.APPROVED.name
                    it[active] = true
                    it[created] = now
                    it[lastUpdated] = now
                }
            }

            ScopeAccessTable.selectAll()
                .where { (ScopeAccessTable.scope eq scope.name) and (ScopeAccessTable.consumerOrgno eq consumerOrgNo) }
                .first()
                .toAccess()
        }
    }

    override fun deleteScopeAccess(consumerOrgNo: String, scope: String): Access {
        val scopeVal = scope
        val consumerVal = consumerOrgNo
        return transaction {
            val existing = ScopeAccessTable.selectAll()
                .where { (ScopeAccessTable.scope eq scopeVal) and (ScopeAccessTable.consumerOrgno eq consumerVal) }
                .firstOrNull()
                ?.toAccess()
                ?: throw NotFoundException("Scope access not found")
            ScopeAccessTable.deleteWhere {
                Op.build { (ScopeAccessTable.scope eq scopeVal) and (ScopeAccessTable.consumerOrgno eq consumerVal) }
            }
            existing
        }
    }
}
