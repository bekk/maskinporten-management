package com.maskinporten.models.changelog

import kotlinx.serialization.Serializable

@Serializable
data class ChangelogResponse(
    val content: List<ChangelogContent>,
    val page: ChangelogPage
)
