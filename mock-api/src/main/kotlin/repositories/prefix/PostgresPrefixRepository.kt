package com.maskinporten.repositories.prefix

import com.maskinporten.models.prefix.Prefix
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.toKotlinInstant

object PrefixesTable : Table("prefixes") {
    val prefix = varchar("prefix", 255)
    val ownerOrgno = varchar("owner_orgno", 20)
    val organizationName = varchar("organization_name", 255).nullable()
    val created = timestampWithTimeZone("created")
    val lastUpdated = timestampWithTimeZone("last_updated")
    val active = bool("active")
    override val primaryKey = PrimaryKey(prefix)
}

class PostgresPrefixRepository : PrefixRepository {

    private fun ResultRow.toPrefix(): Prefix = Prefix(
            prefix = this[PrefixesTable.prefix],
            owner_orgno = this[PrefixesTable.ownerOrgno],
            organization_name = this[PrefixesTable.organizationName],
            created = this[PrefixesTable.created].toInstant().toKotlinInstant(),
            last_updated = this[PrefixesTable.lastUpdated].toInstant().toKotlinInstant(),
            active = this[PrefixesTable.active]
        )

    override fun getPrefix(): List<Prefix> = transaction {
            PrefixesTable.selectAll().map { it.toPrefix() }
        }

    override fun getAllPrefix(includeInactive: Boolean): List<Prefix> = transaction {
            PrefixesTable.selectAll()
                .apply { if (!includeInactive) andWhere { PrefixesTable.active eq true } }
                .map { it.toPrefix() }
        }
}
