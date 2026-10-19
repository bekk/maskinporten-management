package no.kartverket.__pkg__.client

import no.kartverket.__pkg__.client.http.OutgoingHttpClient
import no.kartverket.__pkg__.client.http.OutgoingRequest
import no.kartverket.__pkg__.client.http.OutgoingResponse

public fun json(status: Int, body: String): OutgoingResponse =
    OutgoingResponse(status, "application/json", body.toByteArray())

/** Answers each request with [answer], and keeps it in [requests] so a test can check what was sent. */
public fun recording(requests: MutableList<OutgoingRequest>, answer: (OutgoingRequest) -> OutgoingResponse): OutgoingHttpClient =
    OutgoingHttpClient { request ->
        requests += request
        answer(request)
    }

public fun exampleItemJson(id: String = "abc", name: String = "Example"): String =
    """{"id":"$id","name":"$name","fieldThisClientIgnores":true}"""
