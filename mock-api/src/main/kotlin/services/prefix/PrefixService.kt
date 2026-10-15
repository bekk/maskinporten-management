package com.maskinporten.services.prefix

import com.maskinporten.models.prefix.Prefix
import com.maskinporten.repositories.prefix.PostgresPrefixRepository

class PrefixService {

    val prefixRepository = PostgresPrefixRepository()

    fun getPrefix(organization: String?): List<Prefix> {
        println("[service] getPrefix()")
        return prefixRepository.getPrefix().filter { organization == null || it.owner_orgno == organization }
    }

    fun getAllPrefix(includeInactive: Boolean): List<Prefix> {
        println("[service] getAllPrefix()")
        return prefixRepository.getAllPrefix(includeInactive)
    }
}
