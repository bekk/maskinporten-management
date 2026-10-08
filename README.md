# Maskinporten Management

Management of Maskinporten scopes.

Requests reach the REST server through Istio, where OPA has already checked that the caller may make them. The
server then calls [Digdir's API](https://api.samarbeid.digdir.no/swagger-ui/index.html) for managing Maskinporten
scopes and returns Digdir's response unchanged. Lists like `GET /api/scopes` are the exception: they only keep the
scopes the calling app has access to, which the server gets from `EXTERNAL_FILTERING_URL` (see
[`.env.example`](.env.example)).

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
scripts/dev.sh                   # run the server on http://localhost:8081
curl -i localhost:8081/health/live
```

Before `scripts/dev.sh`, create the local key and fake business certificate, and start the mock API, as its
[README](https://github.com/bekk/maskinporten-management-frontend-mock/tree/main/mock-api) describes.

The server calls Digdir at `DIGDIR_BASE_URL`, with a token from Maskinporten. It gets the token with a JWT grant that
carries the business certificate and is signed in Cloud KMS, where the certificate's private key lives. Locally, the
mock API plays Maskinporten, and the grant is signed with a key from a file (`LOCAL_KMS_KEY_FILE`) instead of Cloud KMS
(`KMS_KEY_VERSION`). In `.env.example` that is the mock API from
[maskinporten-management-frontend-mock](https://github.com/bekk/maskinporten-management-frontend-mock) on port 8080,
so `.env.example` moves the server to port 8081.

The OpenAPI spec is served at `/openapi` and checked in as
[`maskinporten-management-rest-server/openapi.json`](maskinporten-management-rest-server/openapi.json).
After changing the API, regenerate it with:

```bash
./gradlew generateOpenApiSpec
```

## Environment variables

See [`.env.example`](.env.example).
