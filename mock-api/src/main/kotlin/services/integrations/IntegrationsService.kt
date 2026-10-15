package com.maskinporten.services.integrations

import com.maskinporten.repositories.integrations.PostgresIntegrationsRepository
import com.maskinporten.models.integrations.Integration
import kotlin.time.Instant

class IntegrationsService {

    val integrationsRepository = PostgresIntegrationsRepository()

    fun getIntegrations(orgNumber: String?, updatedSince: Instant?): List<Integration> {
        println("[service] getIntegrations(orgNumber: $orgNumber, updatedSince: $updatedSince)")
        return integrationsRepository.getIntegrations(orgNumber, updatedSince)
    }
}
