package com.maskinporten.repositories.organizations

import org.jetbrains.exposed.sql.Table

object OrganizationsTable : Table("organizations") {
    val orgnr = varchar("orgnr", 20)
    val name = varchar("name", 255)
    override val primaryKey = PrimaryKey(orgnr)
}
