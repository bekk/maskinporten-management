# Maskinporten Management

Kotlin library and REST service for managing Maskinporten scopes, and which organisations have access to them,
through Digdir's API.

## About the project

An organisation that offers APIs through Maskinporten manages its scopes in
[Digdir's API](https://api.samarbeid.digdir.no/swagger-ui/index.html): it lists its scopes, and gives and removes
other organisations' access to them. Calling that API takes a Maskinporten client with Digdir's admin scopes, and a
business certificate to sign with.

The REST server holds both, so other apps can manage scopes over plain JSON instead. A call only reaches it after the
platform has checked that the calling app may make it. When filtering is on, lists only show the scopes the calling
app has access to.

> [!NOTE]
> The server answers with its own model of Digdir's data. A field Digdir adds is left out until this project models
> it, and a value Digdir adds to one of its lists of values becomes `UNKNOWN`. When Digdir answers with an error, the
> server passes that answer on unchanged.

## Modules

| Module                                                                       | What it is                                                                  | Published as                                                                  |
| :--------------------------------------------------------------------------- | :-------------------------------------------------------------------------- | :---------------------------------------------------------------------------- |
| [`maskinporten-management-client`](maskinporten-management-client)           | Kotlin client library                                                       | a package, for other services to depend on                                    |
| [`maskinporten-management-rest-server`](maskinporten-management-rest-server) | Ktor server exposing a REST/JSON API                                        | a Docker image, built with [Jib](https://github.com/GoogleContainerTools/jib) |
| [`mock-api`](mock-api)                                                       | Mock of Digdir, Maskinporten and the filtering service, for running locally | nothing, as it is only for local development                                  |


## Prerequisites

- JDK 21, or let the Gradle toolchain resolver provision one
- Docker, for the mock API
- To call Digdir: a Maskinporten client with `idporten:scopes.read` and `idporten:scopes.write`, which Digdir adds by
  hand (servicedesk@digdir.no), and the business certificate, with its private key in Cloud KMS

## Getting started

```bash
./gradlew build                                               # compile, ktlint and tests
mock-api/scripts/setup-local-kms.sh                           # create the local key and fake business certificate
docker compose -f mock-api/docker-compose.yml up -d --build   # start the mock API on http://localhost:8090
cp .env.example .env
scripts/dev.sh                                                # run the server on http://localhost:8080
curl -i localhost:8080/health/live
```

`setup-local-kms.sh` and the mock API need Docker. See the mock's [README](mock-api/README.md) for more. The mock's
tests need its Postgres, so `./gradlew build` skips them; run them with `./gradlew :mock-api:test` while the mock is up.

The server calls Digdir in `DIGDIR_ENVIRONMENT` (`TEST`, `PROD` or `LOCAL`), with a token from Maskinporten in the
same environment. It gets the token with a JWT grant that carries the business certificate and is signed in Cloud KMS
(`DIGDIR_ADMIN_GCP_CLOUD_KMS_KEY_PATH`), where the certificate's private key lives. `.env.example` sets
`DIGDIR_ENVIRONMENT` to `LOCAL`: the mock API on port 8090 then plays both Digdir and Maskinporten, and the server signs
the grant itself, with a key from a file (`DIGDIR_ADMIN_LOCAL_KEY_PATH`).

The mock API also plays the filtering service. To try filtering, set `EXTERNAL_FILTERING_ENABLED=true` in `.env` and
restart the server. Lists then need the `X-Forwarded-Client-Cert` header that Istio sets in production, so set it
yourself, as one of the [mock's test apps](mock-api/README.md#filtering):

```bash
curl -H 'X-Forwarded-Client-Cert: URI=spiffe://cluster.local/ns/tilgangsstyring/sa/demo' localhost:8080/api/scopes
```

## API

| Endpoint                                         | What it is                                          |
| :----------------------------------------------- | :-------------------------------------------------- |
| `GET /api/scopes`                                | Lists the scopes                                    |
| `DELETE /api/scopes?scope=`                      | Deletes a scope                                     |
| `GET /api/scopeaccess/orgs?scope=`               | Lists the organisations that have access to a scope |
| `GET /api/scopeaccess/scopes?orgnr=`             | Lists the scopes an organisation has access to      |
| `PUT /api/scopeaccess/{consumerOrgno}?scope=`    | Gives an organisation access to a scope             |
| `DELETE /api/scopeaccess/{consumerOrgno}?scope=` | Removes an organisation's access to a scope         |
| `GET /health/live`                               | Liveness probe for the platform                     |
| `GET /openapi`                                   | The OpenAPI spec as JSON                            |

The OpenAPI spec documents every field and error, and is checked in as
[`openapi.json`](maskinporten-management-rest-server/openapi.json). Regenerate it after changing the API; the build
fails if it is stale:

```bash
./gradlew generateOpenApiSpec
```

## Environment variables

See [`.env.example`](.env.example) for every variable, what it is and its default. A missing required variable stops
the server at startup.

`DIGDIR_ENVIRONMENT` picks Digdir and Maskinporten: `TEST`, `PROD` or `LOCAL`, the mock API. Set exactly one of the two
key paths: `DIGDIR_ADMIN_GCP_CLOUD_KMS_KEY_PATH` in SKIP, where the key never leaves Cloud KMS, or
`DIGDIR_ADMIN_LOCAL_KEY_PATH` locally, where the server reads the key from a file.

## Logging

The server logs to stdout through logback. `LOGBACK_CONFIG_FILE` replaces the bundled `logback.xml`.

| Logger     | What it logs                                                                                                       |
| :--------- | :----------------------------------------------------------------------------------------------------------------- |
| `access`   | One line per incoming call, unless `ACCESS_LOG_ENABLED` is `false`                                                 |
| `outgoing` | One line before and one after each call to Digdir, Maskinporten and the filtering service, never headers or bodies |

Each line logged during a call shows the calling app's SPIFFE ID, in the MDC field `workload`. A custom
`LOGBACK_CONFIG_FILE` needs `%X{workload}` in its pattern to keep it.

## Useful links

- [Digdir's API for managing scopes](https://api.samarbeid.digdir.no/swagger-ui/index.html)
- [Managing APIs in Maskinporten](https://docs.digdir.no/docs/idporten/oidc/oidc_api_admin_maskinporten)
- [Scopes in ID-porten and Maskinporten](https://docs.digdir.no/docs/idporten/oidc/oidc_protocol_scope)
