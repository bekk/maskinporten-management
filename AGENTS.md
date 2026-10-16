# AGENTS.md

Maskinporten Management manages Kartverket's Maskinporten scopes through
[Digdir's API](https://api.samarbeid.digdir.no/swagger-ui/index.html). It runs in SKIP, behind Istio. See
[README.md](README.md) for how a request flows and [.env.example](.env.example) for every setting.

| Module | What it is |
| --- | --- |
| `maskinporten-management-client` | Kotlin library: calls Digdir, gets Maskinporten tokens, filters scopes. Meant for other services, but no build publishes it yet. |
| `maskinporten-management-rest-server` | Ktor server exposing the client as a REST/JSON API. Shipped as a Docker image built with Jib. |

Kotlin 2.4, Ktor 3, kotlinx.serialization, Gradle with two version catalogs: `gradle/libs.versions.toml`, and Ktor's
own `ktorLibs`, whose version is set in `settings.gradle.kts`. Code compiles for Java 21 (Gradle toolchain, provisioned
if missing), whatever JDK runs Gradle. CI, `.java-version` and the image use Java 25.

## Commands

```bash
./gradlew build                     # compile, ktlint and all tests
./gradlew ktlintFormat              # fix formatting
./gradlew :maskinporten-management-rest-server:test --tests '*.ScopesTest'   # one test class
./gradlew generateOpenApiSpec       # rewrite openapi.json after an API change
scripts/dev.sh                      # run the server on :8080 with the variables in .env
```

Before you finish a change:

1. If anything the spec shows changed (routes, models, enum values, error codes, or the operations in `OpenApi.kt`),
   run `./gradlew generateOpenApiSpec`.
2. Run `./gradlew build`. It must pass.

## Running locally

`scripts/dev.sh` needs a `.env` (`cp .env.example .env`) and the mock API from
[maskinporten-management-frontend-mock](https://github.com/bekk/maskinporten-management-frontend-mock) on port 8090.
Clone the mock next to this repo, at `../maskinporten-management-frontend-mock`, since `.env.example` reads the local
key and certificate chain from there. Set it up as the mock API's
[README](https://github.com/bekk/maskinporten-management-frontend-mock/tree/main/mock-api) describes. With
`DIGDIR_ENVIRONMENT=LOCAL`, the mock plays Digdir and Maskinporten. Filtering is off by default. With
`EXTERNAL_FILTERING_ENABLED=true`, the mock plays the filtering service at `EXTERNAL_FILTERING_URL`, and
`GET /api/scopes` and `GET /api/scopeaccess/scopes` need the `X-Forwarded-Client-Cert` header that Istio sets in
production.

## Conventions

- **Strong types.** A domain value is a `value class` with a private constructor and an `of()`. Where the value has
  rules, `of()` rejects invalid input with `require`, as in `Scope.kt` and `OrganizationNumber.kt`. `AccessToken.of`
  and `ClientCertificate.of` check nothing. Parse at the edge; don't pass raw strings on.
- **Few comments.** Comment only a reason the code cannot show, like the path traversal note in
  `OrganizationNumber.kt`. Don't restate the code, and don't add KDoc to obvious members.
- **Explicit API in the client.** The client module uses `explicitApi()`, so declarations need an explicit
  visibility. Make `public` only what other services need.
- **Our model of Digdir's answers.** The server answers with its own model: Digdir's field names, only the fields we
  describe. An error status from Digdir's API passes through unchanged: status, body and content type. A 401 is the
  exception: Digdir refused our token, not the caller's request, so the server answers 502. Every other
  `DigdirException` is a 502 too: Digdir out of reach, Maskinporten refusing a token, or an answer the client cannot
  read. An enum value Digdir adds later becomes `UNKNOWN` through `OrUnknownSerializer`.
- **The OpenAPI spec is generated.** Every route has `.describe(...)`, with the operations in `OpenApi.kt`, except
  `/openapi` itself, which uses `.hide()`.
  `maskinporten-management-rest-server/openapi.json` comes from `generateOpenApiSpec`, and `OpenApiSpecFileTest` fails
  when it is stale.
- **Settings.** A new setting goes in `application.yaml` as `"$?ENV_VAR"` and is documented in `.env.example`. An
  error about a setting names its `application.yaml` key, like `digdirAdmin.certChain`, and ends with
  `(see .env.example)`.
- **Formatting** is ktlint with the rules in `.editorconfig`. Run `ktlintFormat` instead of formatting by hand.

## Tests

- `kotlin.test` on JUnit 5. Name a test with a sentence in backticks that states the behaviour.
- No mocking library. Fake an `OutgoingHttpClient` with a lambda. In server tests, `json(...)` and `recording(...)` in
  `TestServer.kt` help.
- Server tests start the app with `testServer(...)` from `TestServer.kt` (Ktor's `testApplication`). It skips config
  loading and access logging, so tests of those call `testApplication` directly.
- Shared test data lives in the client's `src/testFixtures`: `DigdirJson` and `TestKeys`.

## Boundaries

- **Always:** keep `./gradlew build` passing. Update README.md and this file when the behaviour they describe changes.
- **Ask first:** adding a dependency; changing the REST API's paths or fields, or the client's public API.
- **Never:** commit `.env`, keys or certificates. Point a local run or a test at Digdir `TEST` or `PROD`. Edit
  `openapi.json` or anything under `build/` by hand.

## Git

Write commit subjects in the imperative, saying what changes for the user of the code, like "Name the setting when
the certificate chain is not PEM". Pull requests follow `.github/PULL_REQUEST_TEMPLATE.md`.
