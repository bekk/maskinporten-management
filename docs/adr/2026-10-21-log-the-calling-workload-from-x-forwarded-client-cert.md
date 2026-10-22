# Log the calling workload from X-Forwarded-Client-Cert

## Status

Accepted

## Context

To find the calls a given app made, or to see which app sent a call, the logs need to name the caller. The server does
no authentication itself: the platform checks the calling app before a call reaches it. When external filtering is on,
the server sends the caller's `X-Forwarded-Client-Cert` header on to the filtering service as it is, and the filtering
service decides which scopes the caller sees. The server reads no identity from the header itself.

Istio, which terminates the caller's mTLS connection, passes the caller's identity on in that header, as a SPIFFE ID
such as `spiffe://cluster.local/ns/team/sa/app`. A proxy that appends its own entry puts it after any entries the caller
sent, so only the last one comes from the proxy.

## Decision

Put the SPIFFE ID from the last entry in `X-Forwarded-Client-Cert` into the MDC as `workload` on every line logged
during a call. Use it for logs only, never to decide what a caller may do. If the header is missing, or its last entry
has no SPIFFE ID, the field is left out.

## Consequences

- Access decisions stay with the platform and the filtering service. The server's own reading of the header is only
  used in logs.
- `workload` can only be trusted where a proxy in front of the server sets or appends the header itself. In any other
  deployment, the caller chooses the value, so it must never be used for anything but finding log lines.
- The filtering service reads the whole header on its own terms, so `workload` is not a record of which identity the
  filtering used.
- Calls without the header are logged as before, just without `workload`.

## References

- The pull request: https://github.com/kartverket/maskinporten-management/pull/42
