# Maskinporten Management

Management of Maskinporten scopes.

A request only reaches the REST server after the platform has checked that the caller may make it. The server
then calls [Digdir's API](https://api.samarbeid.digdir.no/swagger-ui/index.html) for managing Maskinporten
scopes and returns Digdir's response unchanged. Lists like `GET /api/scopes` and `GET /api/scopeaccess/scopes` are
the exception when filtering is on (`EXTERNAL_FILTERING_ENABLED=true`): they only keep the scopes the calling app has
access to, which the server gets from `EXTERNAL_FILTERING_URL`. Filtering is off by default, and then lists show all
scopes (see [`.env.example`](.env.example)).

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

Before `scripts/dev.sh`, create the local key and fake business certificate, and start the mock API, as its
[README](https://github.com/bekk/maskinporten-management-frontend-mock/tree/main/mock-api) describes.

The server calls Digdir in `DIGDIR_ENVIRONMENT` (`TEST`, `PROD` or `LOCAL`), with a token from Maskinporten in the
same environment. It gets the token with a JWT grant that carries the business certificate and is signed in Cloud KMS,
where the certificate's private key lives. Locally, the mock API plays Maskinporten, and the grant is signed with a key
from a file (`DIGDIR_ADMIN_LOCAL_KEY_PATH`) instead of Cloud KMS (`DIGDIR_ADMIN_GCP_CLOUD_KMS_KEY_PATH`). With
`EXTERNAL_FILTERING_ENABLED=true`, the mock API also plays the filtering service, and lists then need the
`X-Forwarded-Client-Cert` header that the platform sets in production; the mock's test frontend sets it for you.
`.env.example` sets `DIGDIR_ENVIRONMENT` to `LOCAL`, the mock API from
[maskinporten-management-frontend-mock](https://github.com/bekk/maskinporten-management-frontend-mock) on port 8090.

The OpenAPI spec is served at `/openapi` and checked in as
[`maskinporten-management-rest-server/openapi.json`](maskinporten-management-rest-server/openapi.json).
After changing the API, regenerate it with:

```bash
./gradlew generateOpenApiSpec
```

## Environment variables

See [`.env.example`](.env.example).

### The business certificate and its key

| Variable | Value | Used |
| --- | --- | --- |
| `DIGDIR_ADMIN_CERT_CHAIN` | The certificate chain itself, in PEM | Always |
| `DIGDIR_ADMIN_GCP_CLOUD_KMS_KEY_PATH` | The key's path in Cloud KMS | In SKIP |
| `DIGDIR_ADMIN_LOCAL_KEY_PATH` | The path to a PEM file with the key | Locally |

Set exactly one of the two key paths. In SKIP, the key never leaves Cloud KMS, so the server only knows where it is
and lets Cloud KMS sign. Locally, the server reads the key from the file and signs itself.
