# Answer errors as Problem Details, with a name as `type`

Status: accepted  
Deciders: Team Tilgangsstyring

## Context and Problem Statement

The server answered its own errors in a JSON format of its own: `error`, a text for people, and `code`, one of
`INVALID_REQUEST`, `UPSTREAM_ERROR` and `INTERNAL_ERROR`. It stopped at the first invalid field, and a wrong path or
method got Ktor's own answer, not ours. When Digdir answered with an error, the server passed that answer on unchanged,
so a caller could get errors in two formats: ours and Digdir's. altinn-pdp, from the same team, had another format of
its own, with other codes.

Problem Details (RFC 9457) is the standard format for errors from HTTP APIs, sent as `application/problem+json`. Should
our errors follow it, and if so, what should say which kind of error it is?

## Decision Drivers

- A caller should handle errors from this API and from altinn-pdp with the same code, in a format that tools and
  libraries already know.
- Code must be able to tell errors apart by a value that does not change.
- A caller should see every invalid field in one answer.
- Nothing to host, and nothing that changes when a repo or a file moves.
- No one uses the API yet, so changing the format is cheap now.

## Considered Options

- Keep our own format
- Problem Details, with `about:blank` as `type` and the kind of error in a `code` field
- Problem Details, with a name for each kind of error as `type`
- Problem Details, with a link to a page that explains the error as `type`

## Decision Outcome

Chosen option: "Problem Details, with a name for each kind of error as `type`", because that is how RFC 9457 means
`type` to be used, and it needs nothing to host. The same kind of error has the same name in this API and in altinn-pdp.

Errors are sent as `application/problem+json`:

```json
{
  "type": "tag:kartverket.no,2026:validation-error",
  "title": "Validation failed",
  "status": 400,
  "errors": [
    { "field": "prefix", "code": "MISSING", "message": "prefix is required" },
    { "field": "Subscope", "code": "NOT_ALLOWED", "message": "Subscope is not allowed" }
  ]
}
```

- `type` says what went wrong, and code should check it. It is a `tag:` URI (RFC 4151): `tag:kartverket.no,2026:`
  followed by a name. It is not a link. `2026` is the year the names were made, and it stays the same in later years.
- `title` is the same for every error of a type.
- `status` is the HTTP status.
- `detail` explains this error, and is left out when the title says it all.
- `errors` comes only with `validation-error`. It lists every field that failed, each with `field`, `code` and
  `message`.

| `type`, after `tag:kartverket.no,2026:` | Status | When |
|---|---|---|
| `validation-error` | 400 | One or more fields are missing or not valid |
| `malformed-request` | 400 | The request cannot be read, for example a body that is not JSON |
| `upstream-rejected` | Digdir's 4xx | Digdir rejected the request |
| `not-found` | 404 | There is no endpoint at the path |
| `method-not-allowed` | 405 | The endpoint does not accept the method |
| `upstream-error` | 502 | Digdir, Maskinporten, the filtering service or Cloud KMS failed |
| `internal-error` | 500 | An unexpected error in this API |

A 4xx from Digdir, other than 401, keeps its status as `upstream-rejected`, with Digdir's `error_description` (or else
`error`) as `detail`. Digdir's whole answer goes to the log. A 401 means Digdir refused this API's token, not the
caller's request, so it becomes a 502 `upstream-error`, like every other status from Digdir.

### Positive Consequences

- Callers can handle errors from this API and from altinn-pdp the same way, and tools that know RFC 9457 can read them.
- A caller sees every invalid field in one answer.
- Digdir's errors come in the same format as ours, with Digdir's explanation kept in `detail`.
- A wrong path or method gets an answer in the same format as every other error.

### Negative Consequences

- Callers that read `error` and `code`, or Digdir's error body, must change.
- The names are part of the API. Renaming one breaks callers, so a new meaning needs a new name. A new kind of error
  that both APIs can have should get the same name in both.
- `type` is not a link, so what each type means is described in the OpenAPI spec.
- Callers no longer get Digdir's error body as it was. It is in this API's log.

## Pros and Cons of the Options

### Keep our own format

- Good, because nothing changes for callers.
- Bad, because callers must learn a format that only this API uses, and altinn-pdp had another one.
- Bad, because tools that know Problem Details cannot read it.

### Problem Details, with `about:blank` as `type` and the kind of error in a `code` field

- Good, because it follows the format, and callers can keep checking `code`.
- Bad, because `about:blank` means "nothing more than the status code", so tools see every error with the same status as
  the same error.
- Bad, because `title` must then be the status text, like "Bad Request".

### Problem Details, with a name for each kind of error as `type`

- Good, because `type` says what went wrong, as RFC 9457 intends.
- Good, because there is nothing to host, and the same error has the same name in both APIs.
- Bad, because the name is not a link, so a person must look in the OpenAPI spec to see what it means.

### Problem Details, with a link to a page that explains the error as `type`

- Good, because RFC 9457 encourages a link to a page that explains the error.
- Bad, because the link depends on where the page is. If the repo or the file moves, `type` changes, and that breaks
  callers.
- Bad, because an error that both APIs have gets two different types, unless both link to the same page.

## References

- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)
- [RFC 4151: The 'tag' URI Scheme](https://www.rfc-editor.org/rfc/rfc4151)
- [The same decision in altinn-pdp](https://github.com/kartverket/altinn-pdp/blob/main/docs/adr/2026-10-22-answer-errors-as-problem-details.md)
- The pull requests: https://github.com/kartverket/maskinporten-management/pull/52, https://github.com/kartverket/maskinporten-management/pull/54, https://github.com/kartverket/maskinporten-management/pull/62
