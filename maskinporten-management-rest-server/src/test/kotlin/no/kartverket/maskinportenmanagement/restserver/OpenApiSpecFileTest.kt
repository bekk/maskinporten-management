package no.kartverket.maskinportenmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenApiSpecFileTest {

    @Test
    fun `the checked-in spec is the one the server serves`() = testApplication {
        application {
            module(testClient(), ExternalFiltering(null))
        }

        val served = client.get("/openapi").bodyAsText() + "\n"
        val file = File("openapi.json")
        if (System.getProperty("openApiSpec.write") == "true") file.writeText(served)

        assertEquals(file.readText(), served, "openapi.json is stale - run ./gradlew generateOpenApiSpec")
    }
}
