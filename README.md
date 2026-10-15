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

| Module                                                                       | What it is                                                                         |
| :--------------------------------------------------------------------------- | :--------------------------------------------------------------------------------- |
| [`maskinporten-management-client`](maskinporten-management-client)           | Kotlin library that talks to Maskinporten and Digdir's API                         |
| [`maskinporten-management-rest-server`](maskinporten-management-rest-server) | Ktor server with a REST/JSON API over the client, built as a Docker image          |
| [`mock-api`](mock-api)                                                       | Mock of Digdir, Maskinporten and the filtering service, for running locally        |

## Prerequisites

- JDK 21, or let the Gradle toolchain resolver provision one
- Docker, for the mock API
- To call Digdir: a Maskinporten client with `idporten:scopes.read` and `idporten:scopes.write`, which Digdir adds by
  hand (servicedesk@digdir.no), and the business certificate, with its private key in Cloud KMS

## Getting started

```bash
./gradlew build   # compile, ktlint and tests
```

`./gradlew build` leaves out the mock API's tests, since they need its Postgres; see its [README](mock-api/README.md).

### Building the client

```kotlin
val httpClient = JavaOutgoingHttpClient(
    HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
    requestTimeout = Duration.ofSeconds(10),
)
val tokenProvider = MaskinportenTokenProvider(
    wellKnownUrl = URI("https://test.maskinporten.no/.well-known/oauth-authorization-server"),
    clientId = "<client id>",
    scopes = listOf(Scope.of("idporten:scopes.read"), Scope.of("idporten:scopes.write")),
    kms = LocalKms.fromPem("<private key in PKCS#8 PEM>"),
    certificateChainPem = "<business certificate chain in PEM, leaf first>",
    httpClient = httpClient,
)
val client = MaskinportenManagementClient(URI("https://api.test.samarbeid.digdir.no"), httpClient, tokenProvider)
```

`kms` signs the grant for the Maskinporten token: `LocalKms` with a key from a file, or your own `Kms`, as the server's
`CloudKms` does for Cloud KMS. `httpClient` can also be your own `OutgoingHttpClient`, but it must not follow
redirects, since the calls carry tokens, and must throw an `IOException` when a call fails.

Build one client and reuse it. The token provider fetches the next token in the background before the current one
runs out. Close it when you are done.

### Calling Digdir

```kotlin
val scopes: List<ScopeDetails> = client.listScopes()
val consumers: List<ScopeAccess> = client.listScopeAccess(Scope.of("<scope>"))
val access: List<ScopeAccess> = client.listConsumerScopeAccess(OrganizationNumber.of("<organisation number>"))
val given: ScopeAccess = client.grantScopeAccess(OrganizationNumber.of("<organisation number>"), Scope.of("<scope>"))
val removed: ScopeAccess = client.removeScopeAccess(OrganizationNumber.of("<organisation number>"), Scope.of("<scope>"))
```

`listScopes` and `listConsumerScopeAccess` take an optional `ScopeFilter`, and then only keep the scopes it allows.

| Exception                      | When                                                                                                          |
| :----------------------------- | :------------------------------------------------------------------------------------------------------------ |
| `DigdirErrorResponseException` | Digdir answered with an error. It holds Digdir's `statusCode`, `contentType` and `body`                       |
| `DigdirException`              | Digdir could not be called, refused the Maskinporten token, or answered with something the client cannot read |

### Running the server

```bash
mock-api/scripts/setup-local-kms.sh                   # the local key and a fake business certificate
docker compose -f mock-api/docker-compose.yml up -d   # the mock API, on port 8090
cp .env.example .env
scripts/dev.sh                                        # the server, on port 8080
curl -i localhost:8080/health/live
```

`.env.example` points the server at the mock API, which plays Digdir and Maskinporten. `dev.sh` loads `.env` into the
environment, which is all the server reads.

With `EXTERNAL_FILTERING_ENABLED=true`, `GET /api/scopes` and `GET /api/scopeaccess/scopes` need the
`X-Forwarded-Client-Cert` header that Istio usually sets in production. Locally, set it yourself, as one of the
[mock's test apps](mock-api/README.md#filtering):

```bash
curl -H 'X-Forwarded-Client-Cert: URI=spiffe://cluster.local/ns/tilgangsstyring/sa/demo' localhost:8080/api/scopes
```

## API

| Endpoint                                         | What it is                                          |
| :----------------------------------------------- | :-------------------------------------------------- |
| `GET /api/scopes`                                | Lists the scopes                                    |
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

| Logger     | What it logs                                                                                                     |
| :--------- | :--------------------------------------------------------------------------------------------------------------- |
| `access`   | One line per incoming call, unless `ACCESS_LOG_ENABLED` is `false`                                               |
| `outgoing` | One line before and one after each call to Digdir, Maskinporten and the filtering service, never headers or bodies |

Each line logged during a call shows the calling app's SPIFFE ID, in the MDC field `workload`. A custom
`LOGBACK_CONFIG_FILE` needs `%X{workload}` in its pattern to keep it.

## Useful links

- [Digdir's API for managing scopes](https://api.samarbeid.digdir.no/swagger-ui/index.html)
- [Managing APIs in Maskinporten](https://docs.digdir.no/docs/idporten/oidc/oidc_api_admin_maskinporten)
- [Scopes in ID-porten and Maskinporten](https://docs.digdir.no/docs/idporten/oidc/oidc_protocol_scope)
