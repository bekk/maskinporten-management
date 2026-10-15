package com.maskinporten.services.scopes.access

import com.maskinporten.maskinporten.requireOwner
import com.maskinporten.models.changelog.ChangelogResponse
import com.maskinporten.models.scopes.access.Access
import com.maskinporten.repositories.changelog.PostgresChangelogRepository
import com.maskinporten.repositories.scopes.PostgresScopesRepository
import com.maskinporten.repositories.scopes.access.PostgresAccessRepository

class AccessService {

    private val scopesAccessRepository = PostgresAccessRepository()
    private val changelogRepository = PostgresChangelogRepository()
    private val scopesRepository = PostgresScopesRepository()


    fun getScopeAccess(consumerOrgNo: String?, scope: String?, includeInactive: Boolean, organization: String?): List<Access> {
        println("[service] getScopesAccess()")
        val scopes = scopesAccessRepository.getScopesAccess(consumerOrgNo, scope, includeInactive)
        return scopes.filter { organization == null || it.owner_orgno == organization }
    }

    fun getScopeAccessChangelog(scopeName: String, page: Int, size: Int, sort: List<String>, organization: String?): ChangelogResponse {
        println("[service] getScopeAccessChangelog()")
        requireOwner(scopesRepository.getScopeByName(scopeName).owner_orgno, organization)
        return changelogRepository.getScopeAccessChangelog(scopeName, page, size, sort)
    }

    fun updateScopeAccess(consumerOrgNo: String, scope: String, organization: String?): Access {
        println("[service] updateScopeAccess()")
        val fullScope = scopesRepository.getScopeByName(scope)
        requireOwner(fullScope.owner_orgno, organization)
        val scopeToUpdate = scopesAccessRepository.updateScopeAccess(consumerOrgNo, fullScope)
        return scopeToUpdate
    }

    fun deleteScopeAccess(consumerOrgNo: String, scope: String, organization: String?): Access {
        println("[service] deleteScopeAccess()")
        requireOwner(scopesRepository.getScopeByName(scope).owner_orgno, organization)
        return scopesAccessRepository.deleteScopeAccess(consumerOrgNo, scope)
    }

    fun getAllScopeAccess(includeInactive: Boolean, organization: String?): List<Access> {
        println("[service] getAllScopesAccess()")
        val allScopes = scopesAccessRepository.getAllScopesAccess(includeInactive)
        return allScopes.filter { organization == null || it.consumer_orgno == organization }
    }
}
