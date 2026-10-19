# AGENTS.md

A Kotlin client (`__name__-client`) and a Ktor REST server (`__name__-rest-server`) that call __Upstream__ for
Kartverket's teams.

## Commands

- `./gradlew build`: compiles, runs ktlint and every test. Must pass before a commit.
- `./gradlew ktlintFormat`: fixes formatting.
- `./gradlew generateOpenApiSpec`: rewrites `__name__-rest-server/openapi.json` after an endpoint changes.
- `scripts/dev.sh`: runs the server with `.env`.

## Layout

- `__name__-client`: no Ktor. Public API is explicit (`explicitApi()`).
  - `__Name__Client`: one method per __Upstream__ operation. Validates input, then calls __Upstream__.
  - `auth/`: `AccessTokenProvider` and its implementation for __Upstream__.
  - `http/`: `OutgoingHttpClient`, the only way the client sends HTTP, so tests can fake it.
  - `exception/`: `__Name__ClientException` (sealed) with `__Upstream__Exception` and `TokenException`.
  - `validation/`: `ValidationException` with every failed field.
  - `src/testFixtures`: fakes and JSON samples that both modules' tests use.
- `__name__-rest-server`:
  - `Main.kt`: `module()` and routes. Each route has `.describe(...)` from `OpenApi.kt`.
  - `__Name__.kt`: builds the client from config and provides it through Ktor DI.
  - `ErrorHandling.kt`: maps every exception to an `ErrorResponse`.
  - `Config.kt`: reads config. Errors name the setting and point to `.env.example`.

## Conventions

- Every error answer is an `ErrorResponse` with an `ErrorCode`. __Upstream__'s error bodies never reach callers.
- A new setting goes in `application.yaml`, `.env.example` (with what it does and its default) and a test.
- A new endpoint gets an OpenAPI description, tests in the server against a fake HTTP client, and a regenerated
  `openapi.json`.
- Logging is not set up yet. It goes in `configureLogging()`, first in `module()`, and outgoing calls are logged by
  wrapping the client in `httpClient()`. Never log headers, bodies or tokens. `AccessToken.toString()` hides the token.
