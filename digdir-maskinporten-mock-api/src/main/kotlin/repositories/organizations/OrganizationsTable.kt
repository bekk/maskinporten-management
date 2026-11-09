package com.maskinporten.repositories.organizations

import org.jetbrains.exposed.v1.core.Table

object OrganizationsTable : Table("organizations") {
    val orgnr = varchar("orgnr", 20)
    val name = varchar("name", 255)
    override val primaryKey = PrimaryKey(orgnr)
}
