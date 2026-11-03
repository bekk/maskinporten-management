package com.maskinporten.services.delegationsources

import com.maskinporten.models.delegationsources.Delegationsource
import com.maskinporten.repositories.delegationsources.PostgresDelegationsourcesRepository

class DelegationsourcesService {

    val delegationsourcesRepository = PostgresDelegationsourcesRepository()

    fun getDelegationsources(): List<Delegationsource> {
        println("[service] getDelegationsources()")
        return delegationsourcesRepository.getAllDelegationsources()
    }
}
