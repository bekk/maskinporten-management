package no.kartverket.maskinportenmanagement.client.support

import no.kartverket.maskinportenmanagement.client.http.JavaManagementHttpClient
import no.kartverket.maskinportenmanagement.client.http.ManagementHttpClient
import java.net.http.HttpClient
import java.time.Duration

internal val testHttpClient: ManagementHttpClient = JavaManagementHttpClient(HttpClient.newHttpClient(), Duration.ofSeconds(10))
