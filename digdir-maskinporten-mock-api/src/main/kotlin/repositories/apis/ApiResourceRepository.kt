package com.maskinporten.repositories.apis

import com.maskinporten.models.apis.ApiResource

interface ApiResourceRepository {
    fun getAllApiResources(): List<ApiResource>
}
