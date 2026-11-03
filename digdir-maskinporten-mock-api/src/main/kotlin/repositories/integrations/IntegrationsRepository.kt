package com.maskinporten.repositories.integrations

import com.maskinporten.models.integrations.Integration
import kotlin.time.Instant

interface IntegrationsRepository {
    fun getIntegrations(customerOrgNumber: String?, updatedSince: Instant?): List<Integration>
}
