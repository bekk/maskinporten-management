package no.kartverket.maskinportenmanagement.client.http

public fun json(status: Int, body: String): OutgoingResponse = OutgoingResponse(status, "application/json", body.toByteArray())

public fun recording(requests: MutableList<OutgoingRequest>, answer: (OutgoingRequest) -> OutgoingResponse): OutgoingHttpClient =
    OutgoingHttpClient { request ->
        requests += request
        answer(request)
    }
