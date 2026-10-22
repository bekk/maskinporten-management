<div align="center">

# 🔐 Fleks · Maskinporten Management

**Kotlin library and REST service for managing Maskinporten scopes, and which organisations have access to them,
through Digdir's API.**

<br/>

[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org)
[![Ktor](https://img.shields.io/badge/Ktor-087CFA?style=flat-square&logo=ktor&logoColor=white)](https://ktor.io)
[![Maskinporten](https://img.shields.io/badge/Maskinporten-0069B4?style=flat-square)](https://docs.digdir.no/docs/Maskinporten/)
[![Status](https://img.shields.io/badge/status-in%20development-orange?style=flat-square)](#-about-the-project)

</div>

---

## 📖 Contents

- [🎯 About the project](#-about-the-project)
- [🧩 Modules](#-modules)
- [📦 Prerequisites](#-prerequisites)
- [🚀 Getting started](#-getting-started)
- [🔌 API](#-api)
- [📚 Using the client](#-using-the-client)
  - [Building the client](#building-the-client)
  - [Calling Digdir](#calling-digdir)
  - [HTTP client](#http-client)
  - [Environments](#environments)
- [🔑 Environment variables](#-environment-variables)
- [📝 Logging](#-logging)
- [🧪 Testing](#-testing)
- [🔗 Useful links](#-useful-links)

---

## 🎯 About the project

An organisation that offers APIs through Maskinporten manages its scopes in
[Digdir's API](https://api.samarbeid.digdir.no/swagger-ui/index.html): it lists its scopes, and gives and removes
other organisations' access to them. Calling that API takes a Maskinporten client with Digdir's admin scopes, and a
business certificate to sign with.

The REST server holds both, so other apps can manage scopes over plain JSON instead. A call only reaches it after the
platform has checked that the calling app may make it. When filtering is on, lists only show the scopes the calling
app has access to.

> [!NOTE]
> The server answers with its own model of Digdir's data. A field Digdir adds is left out until this project models
> it, and a value Digdir adds to one of its lists of values becomes `UNKNOWN`. When Digdir rejects a request, the
> server answers with Digdir's status and explanation, in its own error format.

---

## 🧩 Modules

| Module                                                                       | What it is                                                                  | Published as                                                                  |
| :--------------------------------------------------------------------------- | :-------------------------------------------------------------------------- | :---------------------------------------------------------------------------- |
| [`maskinporten-management-client`](maskinporten-management-client)           | Kotlin library that talks to Maskinporten and Digdir's API directly         | a package, for other services to depend on                                    |
| [`maskinporten-management-rest-server`](maskinporten-management-rest-server) | Ktor server exposing a REST/JSON API over the client                        | a Docker image, built with [Jib](https://github.com/GoogleContainerTools/jib) |
| [`mock-api`](mock-api)                                                       | Mock of Digdir, Maskinporten and the filtering service, for running locally | nothing, as it is only for local development                                  |

`maskinporten-management-rest-server` is the intended consumer of `maskinporten-management-client`, so other systems
can manage scopes over plain JSON without speaking Maskinporten and Digdir's API themselves.

---

## 📦 Prerequisites

- JDK 25, or let the Gradle toolchain resolver provision one
- Docker, for the mock API
- To call Digdir: a Maskinporten client with `idporten:scopes.read` and `idporten:scopes.write`, which Digdir adds by
  hand (servicedesk@digdir.no), and the business certificate, with its private key in Cloud KMS

---

## 🚀 Getting started

```bash
./gradlew build                                               # compile, ktlint and tests
mock-api/scripts/setup-local-kms.sh                           # create the local key and fake business certificate
docker compose -f mock-api/docker-compose.yml up -d --build   # start the mock API on http://localhost:8090
cp .env.example .env
scripts/dev.sh                                                # run the server on http://localhost:8080
curl -i localhost:8080/health/live
```

`setup-local-kms.sh` and the mock API need Docker. See the mock's [README](mock-api/README.md) for more.
`.env.example` sets `DIGDIR_ENVIRONMENT` to `LOCAL`: the mock API then plays both Digdir and Maskinporten, and the
server signs its requests for Maskinporten tokens with a key from a file instead of Cloud KMS.

`dev.sh` is what turns `.env` into real environment variables. The server itself only reads the environment, so
starting it any other way, like an IDE run configuration or `./gradlew :maskinporten-management-rest-server:run`,
means setting them yourself.

The mock API also plays the filtering service. To try filtering, set `EXTERNAL_FILTERING_ENABLED=true` in `.env` and
restart the server. Lists then need the `X-Forwarded-Client-Cert` header that Istio sets in production, so set it
yourself, as one of the [mock's test apps](mock-api/README.md#filtering):

```bash
curl -H 'X-Forwarded-Client-Cert: URI=spiffe://cluster.local/ns/tilgangsstyring/sa/demo' localhost:8080/api/scopes
```

---

## 🔌 API

| Endpoint                                         | What it is                                            |
| :----------------------------------------------- | :---------------------------------------------------- |
| `GET /api/scopes`                                | Lists the scopes                                      |
| `POST /api/scopes`                               | Creates a scope                                       |
| `PUT /api/scopes?scope=`                         | Updates a scope                                       |
| `DELETE /api/scopes?scope=`                      | Deletes a scope                                       |
| `GET /api/scopeaccess/orgs?scope=`               | Lists the organisations that have access to a scope   |
| `GET /api/scopeaccess/scopes?orgnr=`             | Lists the scopes an organisation has access to        |
| `GET /api/scopeaccess/changelog?scope=`          | Lists the changes to who has access to a scope        |
| `PUT /api/scopeaccess/{consumerOrgno}?scope=`    | Gives an organisation access to a scope               |
| `DELETE /api/scopeaccess/{consumerOrgno}?scope=` | Removes an organisation's access to a scope           |
| `GET /health/live`                               | Liveness probe for the platform                       |
| `GET /openapi`                                   | The OpenAPI spec as JSON                              |

The server does no authentication itself: the platform checks the calling app before a call reaches it. When
filtering is on, the server sends the caller's `X-Forwarded-Client-Cert` header, which Istio sets, to the filtering
service, which says which scopes the caller has access to.

Every call gets a request id: the caller's `X-Request-Id` header, or a new UUID when it is missing or not safe to log.
The server logs it on every line of the call as `request_id`, and sends it back in the response's `X-Request-Id`
header, so a caller can tell us which call to look for.

The server's errors follow Problem Details ([RFC 9457](https://www.rfc-editor.org/info/rfc9457/)). They are sent as
`application/problem+json`, with a `code` your code can rely on, and with `VALIDATION_ERROR` an `errors` list of every
field that failed. When Digdir rejects a request, the answer keeps Digdir's status, with `code` `UPSTREAM_REJECTED` and
Digdir's explanation in `detail`.

The OpenAPI spec documents every field and error. The server serves it at `GET /openapi`, and the same spec is checked
in as [`openapi.json`](maskinporten-management-rest-server/openapi.json).

---

## 📚 Using the client

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
    environment = DigdirEnvironment.TEST,
    clientId = "<client id>",
    scopes = listOf(Scope.of("idporten:scopes.read"), Scope.of("idporten:scopes.write")),
    kms = LocalKms.fromPem(privateKeyPem),
    certificateChainPem = certificateChainPem,
    httpClient = httpClient,
)

val client = MaskinportenManagementClient(DigdirEnvironment.TEST, httpClient, tokenProvider)
```

To call other URLs, like the mock API's, give `wellKnownUrl` and `baseUrl` instead of `environment`.

Build one client and reuse it. The token provider signs a JWT grant that carries the business certificate chain,
leaf first, and reuses each token until shortly before it expires, fetching the next one in the background. Close it
when you are done with it.

`LocalKms` signs with a private key you hold, in PKCS #8 PEM. To keep the key in a key management service, implement
`Kms` on it, as the server does for Cloud KMS.

### Calling Digdir

```kotlin
val scopes = client.listScopes()
val consumers = client.listScopeAccess(Scope.of("kartverk:matrikkel.read"))
client.grantScopeAccess(OrganizationNumber.of("311718371"), Scope.of("kartverk:matrikkel.read"))
```

`listScopes` and `listConsumerScopeAccess` take an optional `ScopeFilter`, with exact scopes and prefixes, and only
return the scopes it allows.

When Digdir answers with an error, a call throws a `DigdirErrorResponseException` with Digdir's status, body and
content type. A `DigdirException` means Digdir could not be called, refused the client's own token with a `401`, or
gave an answer the client cannot read.

### HTTP client

`httpClient` takes an `OutgoingHttpClient`, an interface with one method. Use `JavaOutgoingHttpClient`, or implement
it on the HTTP client you already use. Timeouts, proxy and so on are set on that client.

> [!IMPORTANT]
> An `OutgoingHttpClient` must not follow redirects, since the calls carry tokens, and must throw an `IOException`
> when a call fails. `JavaOutgoingHttpClient` refuses a Java `HttpClient` that follows redirects.

The server connects within 2 s and waits at most 10 s per call, since Digdir can be slow.

### Environments

| Environment | Digdir's API (`baseUrl`)               | Maskinporten (`wellKnownUrl`)                                         |
| :---------- | :------------------------------------- | :-------------------------------------------------------------------- |
| `TEST`      | `https://api.test.samarbeid.digdir.no` | `https://test.maskinporten.no/.well-known/oauth-authorization-server` |
| `PROD`      | `https://api.samarbeid.digdir.no`      | `https://maskinporten.no/.well-known/oauth-authorization-server`      |

The server also has `LOCAL`, the mock API in `mock-api` on `http://localhost:8090`. It picks one with
`DIGDIR_ENVIRONMENT`.

---

## 🔑 Environment variables

| Variable                              | Required             | Default | What it is                                                                       |
| :------------------------------------ | :------------------- | :------ | :------------------------------------------------------------------------------- |
| `DIGDIR_ENVIRONMENT`                  | yes                  | -       | Which Digdir and Maskinporten to call: `TEST`, `PROD` or `LOCAL`                 |
| `MASKINPORTEN_CLIENT_ID`              | yes                  | -       | Our Maskinporten client at Digdir                                                |
| `DIGDIR_ADMIN_CERT_CHAIN`             | yes                  | -       | The business certificate chain itself, in PEM, leaf first                        |
| `DIGDIR_ADMIN_GCP_CLOUD_KMS_KEY_PATH` | one of these two     | -       | The certificate's private key in Cloud KMS, in SKIP                              |
| `DIGDIR_ADMIN_LOCAL_KEY_PATH`         | one of these two     | -       | A file with the certificate's private key, locally                               |
| `EXTERNAL_FILTERING_ENABLED`          | no                   | `false` | Whether lists only show the scopes the calling app has access to                 |
| `EXTERNAL_FILTERING_URL`              | when filtering is on | -       | The filtering service                                                            |
| `EXTERNAL_FILTERING_CACHE_SECONDS`    | no                   | `60`    | How long the server remembers what each caller has access to. `0` turns it off   |
| `PORT`                                | no                   | `8080`  | The port the server listens on                                                   |
| `ACCESS_LOG_ENABLED`                  | no                   | `true`  | Whether the server logs one line per call                                        |
| `LOGBACK_CONFIG_FILE`                 | no                   | -       | A `logback.xml` that replaces the bundled one                                    |
| `APPLICATION_CONFIG_FILE`             | no                   | -       | An `application.yaml` that replaces the bundled one                              |

See [`.env.example`](.env.example) for more about each variable and where to get it.

`maskinporten-management-rest-server/src/main/resources/application.yaml` maps each one onto a configuration key, so
a missing required variable stops the server at startup rather than at the first request. A file in
`APPLICATION_CONFIG_FILE` replaces the bundled one rather than merging with it, so it needs every key the bundled one
has.

---

## 📝 Logging

The server logs to stdout through logback, as JSON, set up by the bundled
`maskinporten-management-rest-server/src/main/resources/logback.xml`. To replace it, point `LOGBACK_CONFIG_FILE` at
your own file. The server refuses to start if that path is not a file.

---

## 🧪 Testing

```bash
./gradlew build                 # compile, ktlint, and the tests of the client and the server
./gradlew :mock-api:test        # the mock API's tests, while its Postgres is up
```

`./gradlew build` leaves out the mock API's tests, since they need the Postgres in the mock's Docker Compose. See the
mock's [README](mock-api/README.md#tests). CI runs `./gradlew build` on every pull request.

The build fails if the checked-in `openapi.json` is stale. Regenerate it after changing the API:

```bash
./gradlew generateOpenApiSpec
```

---

## 🔗 Useful links

| Resource                             | Link                                                                    |
| :----------------------------------- | :---------------------------------------------------------------------- |
| Digdir's API for managing scopes     | https://api.samarbeid.digdir.no/swagger-ui/index.html                   |
| Managing APIs in Maskinporten        | https://docs.digdir.no/docs/idporten/oidc/oidc_api_admin_maskinporten   |
| Scopes in ID-porten and Maskinporten | https://docs.digdir.no/docs/idporten/oidc/oidc_protocol_scope           |
| Maskinporten                         | https://docs.digdir.no/docs/Maskinporten                                |

---

<div align="center">
<sub>Made by <b>Fleks-Team Tilgangsstyring</b> at Bekk, for Kartverket</sub>
</div>
