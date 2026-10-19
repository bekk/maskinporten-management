# __Title__

<!-- TODO: One or two sentences on what this does and for whom -->
A Kotlin client and a REST server that call __Upstream__ on behalf of Kartverket's teams, so they do not have to deal
with __Upstream__ themselves.

## Contents

- [About the project](#about-the-project)
- [Modules](#modules)
- [Prerequisites](#prerequisites)
- [Getting started](#getting-started)
- [API](#api)
- [Environment variables](#environment-variables)
- [Testing](#testing)

## About the project

<!-- TODO: What __Upstream__ offers, what this project exposes of it, and how __Upstream__ authenticates us -->

## Modules

| Module | What it is |
| :-- | :-- |
| `__name__-client` | A Kotlin library that calls __Upstream__. Teams that would rather call __Upstream__ themselves can use it directly. |
| `__name__-rest-server` | A Ktor server, run by Team Tilgangsstyring on SKIP, that exposes the client as a REST API. |

## Prerequisites

- Java 21 or later. Gradle downloads the toolchain it needs.

## Getting started

```sh
cp .env.example .env   # and fill in the values
scripts/dev.sh         # starts the server on http://localhost:8080
```

## API

The server describes its API in OpenAPI at `GET /openapi`. The same spec is checked in as
[`__name__-rest-server/openapi.json`](__name__-rest-server/openapi.json); a test fails when they differ. After changing
an endpoint, run:

```sh
./gradlew generateOpenApiSpec
```

Every error answer has the same format: a `code` the caller's code can rely on, and an `error` for people to read.

| Code | Status | Meaning |
| :-- | :-- | :-- |
| `MALFORMED_REQUEST` | 400 | The request could not be read. |
| `VALIDATION_ERROR` | 400 | One or more fields are invalid; `errors` lists them. |
| `UPSTREAM_REJECTED` | 4xx | __Upstream__ rejected the request. The status is __Upstream__'s. |
| `UPSTREAM_ERROR` | 502 | No answer from __Upstream__. The caller's request did not cause it. |
| `INTERNAL_ERROR` | 500 | An unexpected error in this server. |

## Environment variables

See [`.env.example`](.env.example), which lists every variable with what it does and its default.

## Testing

```sh
./gradlew build
```

The tests never call __Upstream__: they pass a fake HTTP client from the client's test fixtures.
