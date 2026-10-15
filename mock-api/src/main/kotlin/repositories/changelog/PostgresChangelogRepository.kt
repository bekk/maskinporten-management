package com.maskinporten.repositories.changelog

import com.maskinporten.models.changelog.ChangelogContent
import com.maskinporten.models.changelog.ChangelogPage
import com.maskinporten.models.changelog.ChangelogResponse
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.toKotlinInstant

object ChangelogTable : Table("changelog") {
    val id = integer("id").autoIncrement()
    val scope = varchar("scope", 255).nullable()
    val created = timestampWithTimeZone("created")
    val message = text("message")
    val actorName = varchar("actor_name", 255)
    val channel = varchar("channel", 50)
    val diff = text("diff")
    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toChangelogContent(): ChangelogContent {
    return ChangelogContent(
        id = this[ChangelogTable.id],
        created = this[ChangelogTable.created].toInstant().toKotlinInstant(),
        message = this[ChangelogTable.message],
        actor_name = this[ChangelogTable.actorName],
        channel = this[ChangelogTable.channel],
        diff = this[ChangelogTable.diff]
    )
}

// Only sorting by created is supported; the direction comes from the first criterion, e.g. "created,desc"
internal fun isDescending(sort: List<String>): Boolean =
    sort.firstOrNull()?.split(",")?.last()?.trim().equals("desc", ignoreCase = true)

class PostgresChangelogRepository : ChangelogRepository {

    override fun getScopeAccessChangelog(scopeName: String, page: Int, size: Int, sort: List<String>): ChangelogResponse {
        return transaction {
            val entries = ChangelogTable.selectAll()
                .where { ChangelogTable.scope eq scopeName }
                .map { it.toChangelogContent() }
            filterChangelogEntries(entries, page, size, sort)
        }
    }

    private fun filterChangelogEntries(entries: List<ChangelogContent>, page: Int, size: Int, sort: List<String>): ChangelogResponse {
        val sortedEntries =
            if (isDescending(sort))
                entries.sortedByDescending { it.created }
            else
                entries.sortedBy { it.created }

        val pagedEntries = sortedEntries.drop(page * size).take(size)

        return ChangelogResponse(
            content = pagedEntries,
            page = ChangelogPage(
                size = size,
                number = page,
                totalElements = entries.size,
                totalPages = if (entries.isEmpty()) 0 else (entries.size + size - 1) / size
            )
        )
    }
}

