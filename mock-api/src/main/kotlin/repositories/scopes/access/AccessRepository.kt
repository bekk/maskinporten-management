package com.maskinporten.repositories.scopes.access

import com.maskinporten.models.scopes.Scope
import com.maskinporten.models.scopes.access.Access

interface AccessRepository {
    fun getAllScopesAccess(includeInactive: Boolean): List<Access>
    fun getScopesAccess(consumerOrgNo: String?, scope: String?, includeInactive: Boolean): List<Access>
    fun updateScopeAccess(consumerOrgNo: String, scope: Scope): Access
    fun deleteScopeAccess(consumerOrgNo: String, scope: String): Access
}
