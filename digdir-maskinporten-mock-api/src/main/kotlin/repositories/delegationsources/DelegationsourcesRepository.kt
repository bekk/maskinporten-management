package com.maskinporten.repositories.delegationsources

import com.maskinporten.models.delegationsources.Delegationsource

interface DelegationsourcesRepository {
    fun getAllDelegationsources(): List<Delegationsource>
}
