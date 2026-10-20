# Read a new scope strictly with Jackson

## Status

Accepted

## Context

`POST /api/scopes` sends the body on to Digdir byte for byte, and the access check in front of the server reads the
scope from `prefix` and `subscope` in the same body. If the two read different values from it, a caller could be
approved for one scope and create another. A name given twice, like `{"subscope":"a","subscope":"b"}`, is the clearest
case: one parser may keep the first value and another the last. The parsers we know of keep the last value today, so
this guards against a change on either side rather than a known gap.

kotlinx.serialization, which the rest of the project uses, cannot refuse such a body. It has no setting that rejects a
name given twice and silently keeps the last value, also when decoding into a class. It also accepts unquoted values
like `True` or `0x10` until they are read. Doing these checks by hand took a scanner that was hard to read and review.

## Decision

`ScopeJson` reads the body with Jackson (`tools.jackson.core:jackson-databind`), set up to refuse:

- a name given twice, at any depth (`STRICT_DUPLICATE_DETECTION`);
- anything after the JSON object (`FAIL_ON_TRAILING_TOKENS`), so the body holds one object only;
- anything that is not strict JSON, which Jackson does by default.

Jackson is used only in `ScopeJson.kt`. The rest of the project keeps kotlinx.serialization.

## Consequences

- Such a body gets 400 "The body is not valid JSON:", followed by Jackson's reason.
- The client library has a second JSON library, with updates to keep up with.
- Any change to how the body is read must keep these three refusals. `ScopeJsonTest.kt` tests them.

## References

- The pull request: https://github.com/kartverket/maskinporten-management/pull/29
