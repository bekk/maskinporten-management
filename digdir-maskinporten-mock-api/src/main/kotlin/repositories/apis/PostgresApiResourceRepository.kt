package com.maskinporten.repositories.apis

import com.maskinporten.models.apis.ApiResource
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object ApiResourcesTable : Table("api_resources") {
    val customerOrganizationNumber = varchar("customer_organization_number", 20)
    val prefix = array<String>("prefix")
    val consumerCount = integer("consumer_count")
    override val primaryKey = PrimaryKey(customerOrganizationNumber)
}

class PostgresApiResourceRepository : ApiResourceRepository {
    override fun getAllApiResources(): List<ApiResource> = transaction {
            ApiResourcesTable.selectAll().map {
                ApiResource(
                    customer_organization_number = it[ApiResourcesTable.customerOrganizationNumber],
                    prefix = it[ApiResourcesTable.prefix].toTypedArray(),
                    consumer_count = it[ApiResourcesTable.consumerCount]
                )
            }
        }
}
