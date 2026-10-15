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
| [`mock-api`](mock-api)                                                   | Mock of Digdir, Maskinporten and the filtering service, for running locally | nothing; it is its own Gradle build, outside `./gradlew build` |

## Prerequisites

- JDK 21, or let the Gradle toolchain resolver provision one

## Getting started

```bash
./gradlew build                                       # compile, ktlint and tests
mock-api/scripts/setup-local-kms.sh                   # create the local key and fake business certificate
docker compose -f mock-api/docker-compose.yml up -d   # start the mock API on http://localhost:8090
cp .env.example .env
scripts/dev.sh                                        # run the server on http://localhost:8080
curl -i localhost:8080/health/live
```

The mock API and the script need Docker. See the mock's [README](mock-api/README.md) for more.

The server calls Digdir at `DIGDIR_BASE_URL`, with a token from Maskinporten. It gets the token with a JWT grant that
carries the business certificate and is signed in Cloud KMS, where the certificate's private key lives. Locally, the
mock API plays Digdir, Maskinporten and the filtering service, and the grant is signed with a key from a file
(`LOCAL_KMS_KEY_FILE`) instead of Cloud KMS (`KMS_KEY_VERSION`). Lists then need the `X-Forwarded-Client-Cert` header
that the platform sets in production, so set it yourself, as one of the
[mock's test apps](mock-api/README.md#filtering):

```bash
curl -H 'X-Forwarded-Client-Cert: URI=spiffe://cluster.local/ns/tilgangsstyring/sa/demo' localhost:8080/api/scopes
```

The OpenAPI spec is served at `/openapi` and checked in as
[`maskinporten-management-rest-server/openapi.json`](maskinporten-management-rest-server/openapi.json).
After changing the API, regenerate it with:

```bash
./gradlew generateOpenApiSpec
```

## Environment variables

See [`.env.example`](.env.example).
