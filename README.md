# Maskinporten Management

Management of Maskinporten scopes.

## Modules

| Module                                                                   | What it is                                   | Published as                                                                  |
| :----------------------------------------------------------------------- | :------------------------------------------- | :---------------------------------------------------------------------------- |
| [`maskinporten-management-client`](maskinporten-management-client)       | Kotlin client library                        | a package, for other services to depend on                                    |
| [`maskinporten-management-rest-server`](maskinporten-management-rest-server) | Ktor server exposing a REST/JSON API      | a Docker image, built with [Jib](https://github.com/GoogleContainerTools/jib) |

## Prerequisites

- JDK 21, or let the Gradle toolchain resolver provision one

## Getting started

```bash
./gradlew build                  # compile, ktlint and tests
cp .env.example .env
scripts/dev.sh                   # run the server on http://localhost:8080
curl -i localhost:8080/health/live
```

The OpenAPI spec is served at `/openapi` and checked in as
[`maskinporten-management-rest-server/openapi.json`](maskinporten-management-rest-server/openapi.json).
After changing the API, regenerate it with:

```bash
./gradlew generateOpenApiSpec
```

## Environment variables

See [`.env.example`](.env.example).
