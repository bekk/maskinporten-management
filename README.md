# Maskinporten Management

Management of Maskinporten scopes.

A request only reaches the REST server after the platform has checked that the caller may make it. The server
then calls [Digdir's API](https://api.samarbeid.digdir.no/swagger-ui/index.html) for managing Maskinporten
scopes and returns Digdir's response unchanged. Lists like `GET /api/scopes` and `GET /api/scopeaccess/scopes` are
the exception: they only keep the scopes the calling app has access to, which the server gets from
`EXTERNAL_FILTERING_URL` (see [`.env.example`](.env.example)).

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
mock API plays Maskinporten and the filtering service, and the grant is signed with a key from a file
(`LOCAL_KMS_KEY_FILE`) instead of Cloud KMS (`KMS_KEY_VERSION`). Lists then need the `X-Forwarded-Client-Cert` header
that the platform sets in production; the mock's test frontend sets it for you. In `.env.example`, `DIGDIR_BASE_URL` is the
mock API from [maskinporten-management-frontend-mock](https://github.com/bekk/maskinporten-management-frontend-mock) on
port 8080, so `.env.example` moves the server to port 8081.

The OpenAPI spec is served at `/openapi` and checked in as
[`maskinporten-management-rest-server/openapi.json`](maskinporten-management-rest-server/openapi.json).
After changing the API, regenerate it with:

```bash
./gradlew generateOpenApiSpec
```

## Logging

Each line logged during a call shows the calling app as `<namespace>/<app>`, for example
`access [team/app] - 200 OK: GET - /api/scopes in 210ms`. Digdir sees every call as coming from this server, so this log is the
only place that shows which app made it. A custom `LOGBACK_CONFIG_FILE` needs `%X{workload}` in its pattern to keep it.

The app comes from the last `URI=spiffe://…` in the `X-Forwarded-Client-Cert` header. Istio's sidecar adds the caller
it verified after anything the caller sent itself (`forwardClientCertDetails: APPEND_FORWARD`), so a caller cannot
pass itself off as another app.

## Environment variables

See [`.env.example`](.env.example).
