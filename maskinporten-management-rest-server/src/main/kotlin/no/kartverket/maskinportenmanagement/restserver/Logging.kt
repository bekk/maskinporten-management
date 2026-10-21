package no.kartverket.maskinportenmanagement.restserver

// The client cert header contains a list of caller IDs. The last one is the most accurate.
internal fun workloadFrom(clientCertificate: String?): String? =
    clientCertificate
        ?.split(',', ';')
        ?.map { it.trim() }
        ?.lastOrNull { it.startsWith("URI=spiffe://") }
        ?.removePrefix("URI=")

private val safeRequestId = Regex("[A-Za-z0-9._:-]{1,128}")

internal fun isSafeRequestId(requestId: String): Boolean = safeRequestId.matches(requestId)
