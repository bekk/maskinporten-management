package com.kartverket.backend.maskinporten.models

import com.fasterxml.jackson.annotation.JsonFormat
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.time.Instant

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ScopePrefix(
    val prefix: String? = null,
    val ownerOrgno: String? = null,
    val organizationName: String? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val created: Instant? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val lastUpdated: Instant? = null,
    val active: Boolean? = null
)
