package com.maskinporten.repositories.integrations

import com.maskinporten.models.integrations.Integration
import com.maskinporten.models.integrations.IntegrationType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

object IntegrationsTable : Table("integrations") {
    val id = varchar("id", 255)
    val description = text("description").nullable()
    val customerOrganizationNumber = varchar("customer_organization_number", 20)
    val supplierOrganizationNumber = varchar("supplier_organization_number", 20).nullable()
    val supplierIntegrationId = varchar("supplier_integration_id", 255).nullable()
    val integrationType = varchar("integration_type", 50)
    val active = bool("active")
    val created = timestampWithTimeZone("created")
    val lastUpdated = timestampWithTimeZone("last_updated")
    override val primaryKey = PrimaryKey(id)
}

class PostgresIntegrationsRepository : IntegrationsRepository {
    override fun getIntegrations(customerOrgNumber: String?, updatedSince: Instant?): List<Integration> = transaction {
            IntegrationsTable.selectAll()
                .apply {
                    customerOrgNumber?.let { andWhere { IntegrationsTable.customerOrganizationNumber eq it } }
                    updatedSince?.let { andWhere { IntegrationsTable.lastUpdated greaterEq it.toJavaInstant().atOffset(ZoneOffset.UTC) } }
                }
                .map {
                    Integration(
                        id = it[IntegrationsTable.id],
                        description = it[IntegrationsTable.description],
                        customer_organization_number = it[IntegrationsTable.customerOrganizationNumber],
                        supplier_organization_number = it[IntegrationsTable.supplierOrganizationNumber],
                        supplier_integration_id = it[IntegrationsTable.supplierIntegrationId],
                        integration_type = IntegrationType.valueOf(it[IntegrationsTable.integrationType]),
                        active = it[IntegrationsTable.active],
                        created = it[IntegrationsTable.created].toInstant().toKotlinInstant(),
                        last_updated = it[IntegrationsTable.lastUpdated].toInstant().toKotlinInstant()
                    )
                }
        }
}
