# Conventions

What every client and REST server project looks like, and why. `template/` is the reference implementation: when
this file and the template disagree, fix one of them.

## Contents

- [Structure](#structure)
- [Errors](#errors)
- [Validation](#validation)
- [JSON](#json)
- [Outgoing HTTP](#outgoing-http)
- [Auth](#auth)
- [Configuration](#configuration)
- [Logging](#logging)
- [OpenAPI](#openapi)
- [Tests](#tests)
- [Build, CI and repo](#build-ci-and-repo)

## Structure

- Two modules: `<name>-client` and `<name>-rest-server`. The client has no Ktor, so teams can use it as a library.
  Its public API is explicit (`explicitApi()`).
- Client packages: the client class and models at the top, then `auth/`, `http/`, `exception/` and `validation/`.
- The upstream environment enum (`<Upstream>Environment`, with `TEST` and `PROD`) lives in the client, since library
  users need it too.
- Server: `Main.kt` holds `main()`, `module()` and the routes. `module()` always runs `configureSerialization`,
  `configureErrorHandling`, `configure<Name>` and then `configureRouting`, in that order, with logging first once it
  is ready (see [Logging](#logging)).
- `configure<Name>(client = clientFromConfig())` provides the client through Ktor DI. Tests pass their own client.
- Endpoints live under `/api/`. `/health/live` and `/openapi` are hidden from the spec.

## Errors

Every error answer is an `ErrorResponse(error, code, errors?)`. `code` is stable for callers' code; `error` is for
people and may change.

| Code | Status | When |
| :-- | :-- | :-- |
| `MALFORMED_REQUEST` | 400 | The request could not be read: a body that is not JSON, a wrong type, a broken URL. |
| `VALIDATION_ERROR` | 400 | Fields are missing or invalid. `errors` lists every one of them. |
| `UPSTREAM_REJECTED` | upstream's 4xx | Upstream refused the request for a reason that is about the request. |
| `UPSTREAM_ERROR` | 502 | Upstream did not answer, answered 5xx, answered something unreadable, refused our token or client (401, 403, 407, 408, 429), or we could not get a token. |
| `INTERNAL_ERROR` | 500 | Anything else. The message is never sent. |

Why we map upstream errors instead of passing them through: callers get one error format whatever is behind us,
upstream details do not leak, and we can change upstream without changing our API. A 401 from upstream means our
token is wrong, not the caller's, so it must never reach the caller as a 401.

In the client, every failure to get an answer is a `<Name>ClientException` (sealed), with `<Upstream>Exception` for
the API call and `TokenException` for getting a token. Both carry `statusCode` and `responseBody` when upstream
answered, and the message shows at most 500 characters of the body.

## Validation

The client validates input before it calls upstream, so library users get the same checks as the server. It throws
`ValidationException` with every failed field, using `ValidationCode.MISSING` or `INVALID_FORMAT`. The server turns
that into `VALIDATION_ERROR` with `errors`. Projects may add codes, such as `CONFLICTING`.

## JSON

- Server: `explicitNulls = false` (null fields are left out of answers) and `ignoreUnknownKeys = true` (requests may
  carry fields we do not know).
- Client: `ignoreUnknownKeys = true`, and models declare only the fields we use, so a field upstream adds does not
  break us.

## Outgoing HTTP

- All HTTP goes through `OutgoingHttpClient` (`OutgoingRequest`/`OutgoingResponse` with `ByteArray` bodies and the
  content type), so tests fake it and the server can wrap it.
- `JavaOutgoingHttpClient` refuses an `HttpClient` that follows redirects: a redirect would send our token elsewhere.
- Timeouts are `CONNECT_TIMEOUT` and `REQUEST_TIMEOUT` in `<Name>.kt`. The values may differ per project.

## Auth

Auth differs per upstream, so only the interface is shared: `AccessTokenProvider` (`accessToken()` and `refused()`)
and `AccessToken`, whose `toString()` hides the token. Each project writes its provider in the client's `auth/`
package. A good provider reuses its token until shortly before it expires and gets a new one after `refused()`.

When several projects need the same kind of provider, such as a Maskinporten JWT grant, consider moving it to a
shared library instead of copying it between them.

## Configuration

- `application.yaml` reads every setting from an environment variable: `$VAR` when required, `$?VAR` when optional.
- `.env.example` lists every variable with what it does and its default. `scripts/dev.sh` exports `.env` and runs the
  server.
- Read settings with the helpers in `Config.kt` (`required`, `optional`, `boolean`, `enum`, `port`, `url`,
  `seconds`, `positiveLong`). Their errors name the setting and point to `.env.example`.

## Logging

The team is working out how we log, so the template has a place for it but no logging of its own yet, only a plain
`logback.xml` that writes to stdout. When the convention is ready, it goes in the template here:

- `configureLogging()` in the server, first in `module()`. A TODO marks the spot.
- Logging of outgoing calls as an `OutgoingHttpClient` that wraps the real one in `httpClient()` in `<Name>.kt`. A
  TODO marks the spot.
- Its settings in `application.yaml` and `.env.example`, like any other setting.

Whatever it ends up as, it never logs headers, bodies or tokens. `AccessToken.toString()` hides the token for that
reason.

## OpenAPI

- The server serves its spec at `/openapi`. `openapi.json` is checked in, and `OpenApiSpecFileTest` fails when it is
  stale: run `./gradlew generateOpenApiSpec`.
- Each route has `.describe(operation)`. Schemas are inferred from the models and documented with `documented(...)`,
  which fails if a documented field no longer exists.
- Every operation documents its 400, 502 and 500 answers with examples. The API version is `v0` until the API is stable.

## Tests

- Nothing calls upstream: tests fake `OutgoingHttpClient` with `recording(...)` and `json(...)` from the client's
  `testFixtures`, which the server's tests use too.
- Server tests go through `testServer(upstream, tokenProvider)`, which wires the server like `module()` does.
- Every endpoint is tested for success, validation, an upstream 4xx and an upstream failure.

## Build, CI and repo

- Gradle with version catalogs (`libs` and Ktor's `ktorLibs`), ktlint (`intellij_idea` style), JVM toolchain 21,
  `org.gradle.caching=true`.
- Jib builds the image from `eclipse-temurin:25-jre-alpine`, runs as `1000:1000` on port 8080. CI builds, pushes to
  ghcr.io and scans with Pharos on every push.
- Every repo has `catalog-info.yaml` (component, client, server, API), `CODEOWNERS`, dependabot, the Kartverket agent
  files, a PR template, `AGENTS.md` and a README with the same sections.
