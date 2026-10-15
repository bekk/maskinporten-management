package com.maskinporten.models.changelog

import kotlinx.serialization.Serializable

@Serializable
data class ChangelogPage(
    val size: Int,
    val number: Int,
    val totalElements: Int,
    val totalPages: Int,
)