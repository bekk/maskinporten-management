# mock-api

A mock for running maskinporten-management locally. It plays the three services the server calls:

- Digdir's API for managing Maskinporten scopes (`/api/v1/...`), from the contract in `maskinporten-api.json`, with
  schema and seed data in `init.sql`
- Maskinporten, which issues the tokens for Digdir's API (see [Maskinporten](#maskinporten))
- the filtering service (see [Filtering](#filtering))

It is built with Ktor and Exposed on Postgres, as a subproject of the root Gradle build: run the commands below from
`mock-api/`. It is only for development, so nothing publishes it, and `./gradlew build` skips its tests.

## Running

Run the API (port 8090) and Postgres (port 5433, seeded from `init.sql` on first start) in Docker:

```sh
docker compose up --build
```

For local development, run only Postgres in Docker and the app with Gradle:

```sh
docker compose up -d db
../gradlew run
```

Swagger UI for the API contract is at `http://localhost:8090/swagger`.

Database settings can be overridden with `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` and `DB_PASSWORD`.

### Resetting the data

`init.sql` only runs when the volume is created. To get back to the seed data:

```sh
docker compose down -v && docker compose up -d db
```

## Local KMS

In production, maskinporten-management signs its Maskinporten grants with Cloud KMS, which holds the private key of
Kartverket's virksomhetssertifikat, and sends the certificate chain in `x5c`. Locally and in tests it uses LocalKms, which
signs with a key from a file instead. Create that key, and a test certificate for it from a fake CA, with:

```sh
./scripts/setup-local-kms.sh
```

This writes to `local-kms/`, which git ignores:

- `virksomhetssertifikat.key`: the private key, for LocalKms
- `virksomhetssertifikat-chain.pem`: the test certificate and the fake CA, for `x5c`, in the same format as the
  certificate chain in production
- `virksomhetssertifikat.crt`: the test certificate alone, with Kartverket's organisation number
- `ca.crt` and `ca.key`: the fake CA, which the fake `/token` trusts

Running the script again is safe: it only creates what is missing, and issues a new certificate before the old one
expires. It needs Docker.

## Maskinporten

maskinporten-management gets its tokens for Digdir's API from Maskinporten, so the mock plays Maskinporten too:

- `GET /.well-known/oauth-authorization-server` names the issuer and the token endpoint.
- `POST /token` takes a JWT grant with the business certificate chain in `x5c`, and checks it within
  [Maskinporten's limits](https://docs.digdir.no/docs/Maskinporten/maskinporten_protocol_jwtgrant): the certificate
  comes from the fake CA in `local-kms/ca.crt`, the signature (RS256, RS384 or RS512) matches it, `aud` is the issuer,
  `iss` and `scope` are there, `iat` is within 10 seconds of now, `exp` has not passed and is at most 180 seconds after
  `iat`, and the grant has not been used before. It returns a token that lasts 120 seconds.
- The mock remembers the organisation number of each token, from the certificate's `organizationIdentifier`
  (`NTRNO-971040238`) or, for older certificates, `serialNumber`, and the scopes the grant asked for.
- `/api/v1` requires one of those tokens as `Authorization: Bearer <token>`, and answers 401 without it. Reading needs
  the scope `idporten:scopes.read` or `idporten:scopes.write`, and changing needs `idporten:scopes.write`, or it answers
  403. As at Digdir, `/scopes/all`, `/prefix/all`, `/apis`, `/delegationsources` and `/integrations` need no token.
- The organisation in the token only sees its own prefixes and scopes, the access it has given in `/scopes/access`, and
  the access given to it in `/scopes/access/all`. Reading or changing another organisation's scope gives 403, and a new
  scope belongs to the organisation that created it.

It is on when `local-kms/ca.crt` exists, so run `./scripts/setup-local-kms.sh` first if you need `/token`. When it is
off, `/api/v1` takes calls without a token.
`MASKINPORTEN_ENABLED=true` makes a missing CA an error, and `false` turns it off. `MASKINPORTEN_CA_FILE` and
`MASKINPORTEN_ISSUER` override the CA (`local-kms/ca.crt`) and the issuer (`http://localhost:8090/`).

## Filtering

maskinporten-management asks a filtering service, at `EXTERNAL_FILTERING_URL`, which scopes the calling app may see.
In production that is OPA. `POST /filtering` plays it, in OPA's format: maskinporten-management sends the
`X-Forwarded-Client-Cert` header it got as `{"input": {"headers": {"x-forwarded-client-cert": "..."}}}`, and the mock
takes the app from the last SPIFFE URI in it and answers `{"result": {"exact": [...], "prefix": [...]}}`. Unknown apps
get empty lists.

| App (`<namespace>/<app>`) | Sees |
| ------------------------- | ---- |
| `tilgangsstyring/demo`    | Scopes starting with `kartverk:tilgangsstyring/` |
| `matrikkel/matrikkel-api` | `kartverk:matrikkel.read`, `kartverk:matrikkel.write` and `kartverk:adresse.read` |

To try it, run maskinporten-management with `EXTERNAL_FILTERING_ENABLED=true` and
`EXTERNAL_FILTERING_URL=http://localhost:8090/filtering`, as in [`.env.example`](../.env.example), and set the header
the platform sets in production yourself:

```sh
curl -H 'X-Forwarded-Client-Cert: URI=spiffe://cluster.local/ns/tilgangsstyring/sa/demo' http://localhost:8080/api/scopes
```

## Tests

```sh
../gradlew test
```

Tests need the compose Postgres running. They use a separate `maskinporten-test` database
(created automatically) which is wiped and reseeded from `init.sql` before each test, so they
never touch the dev data. Use the `testApp { }` helper in `src/test/kotlin/TestApp.kt` for new tests.
