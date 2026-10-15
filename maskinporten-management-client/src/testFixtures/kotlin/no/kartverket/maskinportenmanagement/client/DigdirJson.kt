package no.kartverket.maskinportenmanagement.client

public fun digdirScope(name: String): String = """
    {"name":"$name","prefix":"${name.substringBefore(':')}","subscope":"${name.substringAfter(':')}",
    "description":"Tilgang til $name","active":true,"created":"2026-01-15T09:30:00.123456Z",
    "last_updated":"2026-01-15T09:30:00Z","allowed_integration_types":["maskinporten"],"owner_orgno":"971040238",
    "token_type":"SELF_CONTAINED","visibility":"PUBLIC","requires_user_consent":false,
    "requires_user_authentication":false,"requires_pseudonymous_tokens":false,"at_max_age":120,
    "authorization_max_lifetime":0,"accessible_for_all":false,"protected":false,"supports_european_businesses":false}
""".trimIndent()

public fun digdirScopeAccess(scope: String, consumerOrgno: String = "311718371"): String = """
    {"scope":"$scope","owner_orgno":"971040238","owner_organization_name":"STATENS KARTVERK",
    "consumer_orgno":"$consumerOrgno","consumer_organization_name":"TESTORGANISASJON","state":"APPROVED",
    "created":"2026-01-15T09:30:00Z","last_updated":"2026-01-15T09:30:00Z"}
""".trimIndent()

public fun jsonList(vararg items: String): String = items.joinToString(",", "[", "]")
