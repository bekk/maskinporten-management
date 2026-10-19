---
name: new-rest-proxy
description: Scaffolds a new Kartverket project with a Kotlin client library and a Ktor REST server that proxies an external API, with the team's folder structure, Gradle, ktlint, OpenAPI, error handling, tests, CI and Backstage set up the same way in every project. It names everything after the project but writes no real API calls, URLs or auth. Use when the user wants to create, set up or scaffold a new client and REST server, proxy or repo like the team's existing ones (altinn-pdp, maskinporten-management).
---

# New REST proxy

Creates the skeleton for a new project, set up like every other one: `<name>-client` (plain Kotlin, no Ktor) and
`<name>-rest-server` (Ktor), with a place for everything (auth, upstream calls, errors, config, logging) and one
example endpoint that runs through both, so the build is green from the start. Files, classes, exceptions and tests
are named after the project. Why the skeleton looks the way it does is in [CONVENTIONS.md](CONVENTIONS.md).

## Quick start

```sh
scripts/render.sh <name> "<Title>" <Upstream> <target-dir>
cd <target-dir> && ./gradlew build
```

`scripts/` is relative to this skill's directory.

## Only scaffold

The project is a starting point for the team, who write the specifics later. Do not add any of these, even when you
know the upstream API, and even when another repo already has them:

- calls to upstream, endpoints, models or validation beyond the example
- URLs, scopes, client ids or other upstream details
- a token provider, or any other auth beyond the `AccessTokenProvider` interface
- logging beyond the plain `logback.xml`

Leave the TODOs in place: they tell the team where each of these goes.

## Workflow

- [ ] **1. Names.** Work out the names from what the user said, and ask only for what you cannot:
  - name: kebab-case, also the GitHub repo name.
  - title: the name for people, in words. Keep acronyms the user writes, such as `PDP`.
  - upstream: the API it calls, one PascalCase word. Often the first word of the name, but not always: in
    maskinporten-management it is `Digdir`. Ask when it is not clear.
  - target directory, which must not exist: a sibling of the user's other repos, unless they say otherwise.

  Tell the user the names in one line, then go on.
- [ ] **2. Render.** Run `scripts/render.sh <name> "<Title>" <Upstream> <target-dir>`. It copies `template/`, fills
      in the names in contents and paths, runs `ktlintFormat` and writes `openapi.json`.
- [ ] **3. Build.** Run `./gradlew build` in the new project. It must pass. If it does not, the template is wrong:
      fix `template/` and render again, rather than patching the new project.
- [ ] **4. Hand over.** Say the project is created and builds, and list what the team fills in, from
      `grep -rn TODO --exclude-dir=build .`: the base URLs, the token provider, the real endpoints in place of the
      example, the descriptions in `OpenApi.kt`, `README.md` and `catalog-info.yaml` (whose `System` must go if
      another repo already defines it), and logging when the convention is ready. Leave `TODO: DHI` in the server's
      `build.gradle.kts`: it is a team-wide TODO. Do not `git init`, commit or push unless the user asks.

## Changing the template

Change `template/` and `CONVENTIONS.md` together, so the next project gets the change. `render.sh` derives the
placeholders from its arguments, in file contents and paths:

| Placeholder | From | Placeholder | From |
| :-- | :-- | :-- | :-- |
| `__name__` | name as given | `__Upstream__` | upstream as given |
| `__pkg__` | name without hyphens | `__upstream__` | upstream in lowercase |
| `__Name__` | name in PascalCase | `__UPSTREAM__` | upstream in uppercase |
| `__Title__` | title as given | | |

After a change, render into a temporary directory and run `./gradlew build` there. Try an upstream that sorts after
`Token` too, since import order depends on the names.
