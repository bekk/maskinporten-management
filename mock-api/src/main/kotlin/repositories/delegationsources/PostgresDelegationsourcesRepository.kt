package com.maskinporten.repositories.delegationsources

import com.maskinporten.models.delegationsources.Delegationsource
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.toKotlinInstant

object DelegationsourcesTable : Table("delegationsources") {
    val id = varchar("id", 255)
    val name = varchar("name", 255)
    val created = timestampWithTimeZone("created")
    val lastUpdated = timestampWithTimeZone("last_updated")
    override val primaryKey = PrimaryKey(id)
}

class PostgresDelegationsourcesRepository : DelegationsourcesRepository {
    override fun getAllDelegationsources(): List<Delegationsource> {
        return transaction {
            DelegationsourcesTable.selectAll().map {
                Delegationsource(
                    issuer = it[DelegationsourcesTable.id],
                    name = it[DelegationsourcesTable.name],
                    created = it[DelegationsourcesTable.created].toInstant().toKotlinInstant(),
                    lastUpdated = it[DelegationsourcesTable.lastUpdated].toInstant().toKotlinInstant()
                )
            }
        }
    }
}
