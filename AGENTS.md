# AGENTS.md

Maskinporten Management manages Kartverket's Maskinporten scopes through
[Digdir's API](https://api.samarbeid.digdir.no/swagger-ui/index.html). The server does no authentication itself: the
platform checks the calling app before a call reaches it. When external filtering is on, the server sends the
caller's `X-Forwarded-Client-Cert` header to the filtering service, which decides which scopes the caller sees. See
[README.md](README.md) for its API and how to run it, and [.env.example](.env.example) for every setting.

| Module | What it is |
| --- | --- |
| `maskinporten-management-client` | Kotlin library for other services: calls Digdir, gets Maskinporten tokens, filters scopes. |
| `maskinporten-management-rest-server` | Ktor server exposing the client as a REST/JSON API. Shipped as a Docker image built with Jib. |
| `mock-api` | Mock of Digdir, Maskinporten and the filtering service, for running locally. |

Kotlin 2.4, Ktor 3, kotlinx.serialization (Jackson only in `ScopeJson.kt`, to read a new scope strictly), Gradle with
two version catalogs: `gradle/libs.versions.toml`, and Ktor's own `ktorLibs`, whose version is set in
`settings.gradle.kts`. Code compiles for Java 25 (Gradle toolchain, provisioned if missing), whatever JDK runs Gradle.
CI, `.java-version` and the server's image use Java 25.

## Commands

```bash
./gradlew build                     # compile, ktlint and all tests except the mock API's
./gradlew ktlintFormat              # fix formatting
./gradlew :maskinporten-management-rest-server:test --tests '*.ScopesTest'   # one test class
./gradlew :mock-api:test            # the mock API's tests, while the mock is up
./gradlew generateOpenApiSpec       # rewrite openapi.json after an API change
scripts/dev.sh                      # run the server on :8080 with the variables in .env
```

Before you finish a change:

1. Run `./gradlew generateOpenApiSpec`. It is quick, and `openapi.json` only changes if the spec did.
2. Run `./gradlew build`. It must pass.
3. If you changed `mock-api`, also run `./gradlew :mock-api:test`, as [its README](mock-api/README.md#tests) describes.

## Running locally

Follow README.md's [Getting started](README.md#-getting-started). It starts the mock API in `mock-api`, which plays
Digdir, Maskinporten and the filtering service.

## Conventions

These conventions and the tests below are for the client and the server. `mock-api` has its own style; for its
tests, see [its README](mock-api/README.md#tests).

- **Strong types.** A domain value is a `value class` with a private constructor and an `of()`. Where the value has
  rules, `of()` rejects invalid input with `require`, as in `Scope.kt` and `OrganizationNumber.kt`. `AccessToken.of`
  and `ClientCertificate.of` check nothing. In new code, parse at the edge and don't pass raw strings on. Older public
  API, like `ScopeFilter`, still takes strings; changing it changes the client's public API.
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
- **Settings.** A new setting goes in `application.yaml` as `"$?ENV_VAR"` and is documented in `.env.example`. Read
  it with the helpers in `Config.kt`, like `required` and `seconds`: they treat an empty value as unset, and their
  errors name the `application.yaml` key, like `digdirAdmin.certChain`, and end with `(see .env.example)`. Write any
  other error about a setting the same way.
- **Formatting** in every module, `mock-api` too, is ktlint with the rules in `.editorconfig`. Run `ktlintFormat`
  instead of formatting by hand.

## Tests

- `kotlin.test` on JUnit 5. Name a test with a sentence in backticks that states the behaviour.
- No mocking library. Fake an `OutgoingHttpClient` with a lambda. In server tests, `json(...)` and `recording(...)` in
  `TestServer.kt` help.
- Server tests start the app with `testServer(...)` from `TestServer.kt` (Ktor's `testApplication`). It skips config
  loading and access logging, so tests of those call `testApplication` directly.
- Shared test data lives in the client's `src/testFixtures`: `DigdirJson.kt` and `TestKeys.kt`.

## Decisions (ADRs)

Important decisions are recorded as Architecture Decision Records in `docs/adr`. Examples are choosing a library or
framework, changing the API contract, and security-relevant choices. Read the relevant ADRs before changing what they
cover. When a change makes a decision like this, suggest an ADR, but don't write one unless asked.

There are two templates. Pick the one that fits how much is at stake, or ask for guidance if unsure:

- **Brief** (`template-brief.md`): the decision is easy to reverse, or the best option is fairly obvious. A few
  sentences each for the context, the decision and the consequences are enough.
- **Detailed** (`template-detailed.md`): the decision is costly to reverse, affects callers or other teams, or real
  alternatives were weighed. It needs the full problem statement, the options considered with their pros and cons,
  and why the chosen option won. Ask for guidance on the list of deciders (the people or teams who took part).

Name files `YYYY-MM-DD-title.md`, using the date of the decision and a lowercase, hyphenated title. Base an ADR on
where the decision was made, like a pull request or a card, and link it under References. Never change or rewrite an
accepted ADR, other than updating its status if it is superseded. Write a new one that supersedes it, and update the
old one's status.

## Boundaries

- **Always:** keep `./gradlew build` passing. Update README.md and this file when the behaviour they describe changes,
  and keep the environment variable table in README.md in step with `.env.example`.
- **Ask first:** adding a dependency; changing the REST API's paths or fields, or the client's public API.
- **Never:** commit `.env`, keys or certificates; point a local run or a test at Digdir `TEST` or `PROD`; or edit
  `openapi.json` or anything under `build/` by hand.

## Git

Write commit subjects in the imperative, saying what changes for the user of the code, like "Name the setting when
the certificate chain is not PEM". Pull requests follow `.github/PULL_REQUEST_TEMPLATE.md`.
