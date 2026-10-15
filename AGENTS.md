# AGENTS.md

Maskinporten Management manages Kartverket's Maskinporten scopes through
[Digdir's API](https://api.samarbeid.digdir.no/swagger-ui/index.html). It runs in SKIP, behind Istio. See
[README.md](README.md) for how a request flows and [.env.example](.env.example) for every setting.

| Module | What it is |
| --- | --- |
| `maskinporten-management-client` | Kotlin library: calls Digdir, gets Maskinporten tokens, filters scopes. Published for other services. |
| `maskinporten-management-rest-server` | Ktor server exposing the client as a REST/JSON API. Shipped as a Docker image built with Jib. |

Kotlin 2.4, Ktor 3, kotlinx.serialization, Gradle with a version catalog (`gradle/libs.versions.toml`). Code compiles
for Java 21 (Gradle toolchain, provisioned if missing). Gradle and the image run on Java 25.

## Commands

```bash
./gradlew build                     # compile, ktlint and all tests
./gradlew ktlintFormat              # fix formatting
./gradlew :maskinporten-management-rest-server:test --tests '*ScopesTest'   # one test class
./gradlew generateOpenApiSpec       # rewrite openapi.json after an API change
scripts/dev.sh                      # run the server on :8080 with the variables in .env
```

Before you finish a change:

1. If routes, request or response models changed, run `./gradlew generateOpenApiSpec`.
2. Run `./gradlew build`. It must pass.

## Running locally

`scripts/dev.sh` needs a `.env` (`cp .env.example .env`) and the mock API from
[maskinporten-management-frontend-mock](https://github.com/bekk/maskinporten-management-frontend-mock) on port 8090.
With `DIGDIR_ENVIRONMENT=LOCAL`, the mock plays Digdir, Maskinporten and the filtering service. Set it up as the
mock's README describes. With `EXTERNAL_FILTERING_ENABLED=true`, list endpoints need the `X-Forwarded-Client-Cert`
header that Istio sets in production.

## Conventions

- **Strong types.** A domain value is a `value class` with a private constructor and an `of()` that rejects invalid
  input with `require`. See `Scope.kt` and `OrganizationNumber.kt`. Parse at the edge; don't pass raw strings on.
- **Few comments.** Comment only a reason the code cannot show, like the path traversal note in
  `OrganizationNumber.kt`. Don't restate the code, and don't add KDoc to obvious members.
- **Explicit API in the client.** The client module uses `explicitApi()`, so every declaration needs `public` or
  `internal`. Keep anything other services don't need `internal`.
- **Our model of Digdir's answers.** The server answers with its own model: Digdir's field names, only the fields we
  describe. A Digdir error passes through unchanged: status, body and content type. An enum value Digdir adds later
  becomes `UNKNOWN` through `OrUnknownSerializer`.
- **The OpenAPI spec is generated.** Every route has `.describe(...)`, with the operations in `OpenApi.kt`.
  `maskinporten-management-rest-server/openapi.json` comes from `generateOpenApiSpec`, and `OpenApiSpecFileTest` fails
  when it is stale.
- **Settings.** A new setting goes in `application.yaml` as `"$?ENV_VAR"` and is documented in `.env.example`. An
  error about a setting names it and ends with `(see .env.example)`.
- **Formatting** is ktlint with the rules in `.editorconfig`. Run `ktlintFormat` instead of formatting by hand.

## Tests

- `kotlin.test` on JUnit 5. Name a test with a sentence in backticks that states the behaviour.
- No mocking library. Fake an `OutgoingHttpClient` with a lambda; `json(...)` and `recording(...)` in `TestServer.kt`
  help.
- Server tests start the app with `testServer(...)` from `TestServer.kt` (Ktor's `testApplication`).
- Shared test data lives in the client's `src/testFixtures`: `DigdirJson` and `TestKeys`.

## Boundaries

- **Always:** keep `./gradlew build` passing. Update README.md and this file when the behaviour they describe changes.
- **Ask first:** adding a dependency; changing the REST API's paths or fields, or the client's public API.
- **Never:** commit `.env`, keys or certificates. Point the server at Digdir `TEST` or `PROD`. Edit `openapi.json` or
  anything under `build/` by hand.

## Git

Write commit subjects in the imperative, saying what changes for the user of the code, like "Name the setting when
the certificate chain is not PEM". Pull requests follow `.github/PULL_REQUEST_TEMPLATE.md`.
