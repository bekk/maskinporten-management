# Log JSON with logstash-logback-encoder

## Status

Accepted

## Context

The server logged plain text lines through a logback pattern. Log tools have to parse such lines before they can search
on a field, and the new MDC fields `workload` and `request_id` would only be searchable if every pattern printed them in
the same way.

Logback 1.6 ships `ch.qos.logback.classic.encoder.JsonEncoder`, so JSON needs no new dependency. It nests the MDC under
an `mdc` object, however, and its field names cannot be changed. `net.logstash.logback:logstash-logback-encoder` puts
each MDC entry at the top level of the line and lets each field be renamed or left out.

## Decision

Log JSON to stdout with `LogstashEncoder` from `logstash-logback-encoder` in the bundled `logback.xml`, leaving out the
`version`, `thread` and `levelValue` fields.

## Consequences

- `workload` and `request_id` are top-level fields on every line, so they can be searched as they are.
- `logstash-logback-encoder` needs Jackson 3. The client already uses Jackson 3 (`tools.jackson.core:jackson-databind`)
  in `ScopeJson.kt`, so Gradle resolves both to the client's version, and the server gets no second JSON library. It
  is still one more dependency to keep up to date.
- A deployment that wants another format can still replace the bundled file through `LOGBACK_CONFIG_FILE`.

## References

- The pull request: https://github.com/kartverket/maskinporten-management/pull/42
