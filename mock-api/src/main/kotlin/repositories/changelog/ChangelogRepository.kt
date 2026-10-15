package com.maskinporten.repositories.changelog

import com.maskinporten.models.changelog.ChangelogResponse

interface ChangelogRepository {
    fun getScopeAccessChangelog(scopeName: String, page: Int, size: Int, sort: List<String>): ChangelogResponse
}