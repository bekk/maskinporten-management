package com.maskinporten.repositories.scopes

import com.maskinporten.dto.CreateScopeRequest
import com.maskinporten.dto.UpdateScopeRequest
import com.maskinporten.models.scopes.IntegrationType
import com.maskinporten.models.scopes.Scope

interface ScopesRepository {
    fun getAllScopes(
        prefix: String?,
        includeInactive: Boolean,
        onlyWithDelegationSource: Boolean,
        onlyAccessibleForAll: Boolean,
        integrationType: IntegrationType?
    ): List<Scope>
    fun getScopes(inactive: Boolean): List<Scope>
    fun getScopeByName(name: String): Scope
    fun createScope(scope: CreateScopeRequest): Scope
    fun updateScope(name: String, scope: UpdateScopeRequest): Scope
    fun deleteScope(name: String): Scope
}

