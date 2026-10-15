package com.maskinporten.services.scopes

import com.maskinporten.dto.CreateScopeRequest
import com.maskinporten.dto.UpdateScopeRequest
import com.maskinporten.maskinporten.ForbiddenException
import com.maskinporten.maskinporten.requireOwner
import com.maskinporten.models.scopes.Scope
import com.maskinporten.models.scopes.IntegrationType
import com.maskinporten.repositories.prefix.PostgresPrefixRepository
import com.maskinporten.repositories.scopes.PostgresScopesRepository

class ScopesService {

    val scopesRepository = PostgresScopesRepository()
    private val prefixRepository = PostgresPrefixRepository()

    fun getScopes(inactive: Boolean = false, organization: String?): List<Scope> {
        println("[service] getScopes()")
        return scopesRepository.getScopes(inactive).filter { organization == null || it.owner_orgno == organization }
    }

    fun createScope(scope: CreateScopeRequest, organization: String?): Scope {
        println("[service] createScope()")
        if (organization == null) return scopesRepository.createScope(scope)
        requirePrefixOwner(scope.prefix, organization)
        return scopesRepository.createScope(scope.copy(owner_orgno = organization))
    }

    fun updateScope(scope: String, request: UpdateScopeRequest, organization: String?): Scope {
        println("[service] updateScope()")
        if (organization == null) return scopesRepository.updateScope(scope, request)
        requireOwner(scopesRepository.getScopeByName(scope).owner_orgno, organization)
        return scopesRepository.updateScope(scope, request.copy(owner_orgno = organization))
    }

    fun deleteScope(scope: String, organization: String?): Scope {
        println("[service] deleteScope()")
        requireOwner(scopesRepository.getScopeByName(scope).owner_orgno, organization)
        return scopesRepository.deleteScope(scope)
    }

    fun getScopeByName(scope: String, organization: String?): Scope {
        println("[service] getScopeByName()")
        return scopesRepository.getScopeByName(scope).also { requireOwner(it.owner_orgno, organization) }
    }

    fun getAllScopes(
        prefix: String?,
        includeInactive: Boolean = false,
        onlyWithDelegationSource: Boolean = false,
        onlyAccessibleForAll: Boolean = false,
        integrationType: IntegrationType?
    ): List<Scope> {
        println("[service] getAllScopes()")
        return scopesRepository.getAllScopes(
            prefix,
            includeInactive,
            onlyWithDelegationSource,
            onlyAccessibleForAll,
            integrationType
        )
    }

    // A prefix that is not registered is not the organisation's either
    private fun requirePrefixOwner(prefix: String, organization: String) {
        val owner = prefixRepository.getPrefix().find { it.prefix == prefix }?.owner_orgno
        if (owner != organization) throw ForbiddenException("The prefix does not belong to the organisation")
    }
}
