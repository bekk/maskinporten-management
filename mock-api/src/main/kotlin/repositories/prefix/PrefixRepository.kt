package com.maskinporten.repositories.prefix

import com.maskinporten.models.prefix.Prefix

interface PrefixRepository {
    fun getPrefix(): List<Prefix>
    fun getAllPrefix(includeInactive: Boolean): List<Prefix>
}
