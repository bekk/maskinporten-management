package com.maskinporten.models.apis

import kotlinx.serialization.Serializable

@Serializable
data class ApiResource(
    val customer_organization_number: String,
    val prefix: Array<String>,
    val consumer_count: Int
)
