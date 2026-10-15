package com.maskinporten.services.apis

import com.maskinporten.models.apis.ApiResource
import com.maskinporten.repositories.apis.PostgresApiResourceRepository

class ApisService {

    val apiResourceRepository = PostgresApiResourceRepository()

    fun getApis(): List<ApiResource> {
        println("[service] getApis()")
        return apiResourceRepository.getAllApiResources()
    }
}
