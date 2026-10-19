#!/usr/bin/env bash
# Copies the template to a new project and fills in its names.
#
#   render.sh <name> <title> <upstream> <target-dir>
#
#   name      kebab-case repo and module prefix, e.g. altinn-pdp
#   title     human name, e.g. "Altinn PDP"
#   upstream  the API it calls, one word in PascalCase, e.g. Altinn
#
# From these it derives the placeholders in the template:
#   __name__ altinn-pdp   __pkg__ altinnpdp   __Name__ AltinnPdp   __Title__ Altinn PDP
#   __Upstream__ Altinn   __upstream__ altinn   __UPSTREAM__ ALTINN
#
# Then it runs ktlintFormat and generateOpenApiSpec in the new project, which needs Java and network for Gradle.
set -euo pipefail

if [ $# -ne 4 ]; then
    sed -n "2,15p" "$0" | sed 's/^# \{0,1\}//' >&2
    exit 64
fi

name=$1 title=$2 upstream=$3 target=$4
template="$(cd "$(dirname "$0")/../template" && pwd)"

fail() { echo "render.sh: $*" >&2; exit 1; }

[[ $name =~ ^[a-z][a-z0-9]*(-[a-z0-9]+)*$ ]] || fail "name must be kebab-case, like altinn-pdp, but was \"$name\""
[[ $upstream =~ ^[A-Z][A-Za-z0-9]*$ ]] || fail "upstream must be one PascalCase word, like Altinn, but was \"$upstream\""
[[ -n $title && $title != *[\"\\\$\`]* ]] || fail "title must not be empty or contain \" \\ \$ or \`"
[ ! -e "$target" ] || fail "$target already exists"

pkg=${name//-/}
pascal=""
IFS='-' read -ra parts <<< "$name"
for part in "${parts[@]}"; do pascal+="$(tr '[:lower:]' '[:upper:]' <<< "${part:0:1}")${part:1}"; done
lower=$(tr '[:upper:]' '[:lower:]' <<< "$upstream")
upper=$(tr '[:lower:]' '[:upper:]' <<< "$upstream")
[ "${pascal}Client" != "$upstream" ] || fail "upstream must not be ${pascal}Client, as the client is named that"

mkdir -p "$(dirname "$target")"
cp -R "$template" "$target"

export R_NAME=$name R_PKG=$pkg R_PASCAL=$pascal R_TITLE=$title R_UP=$upstream R_UP_LOWER=$lower R_UP_UPPER=$upper
substitute='s/__name__/$ENV{R_NAME}/g; s/__pkg__/$ENV{R_PKG}/g; s/__Name__/$ENV{R_PASCAL}/g; s/__Title__/$ENV{R_TITLE}/g;
    s/__Upstream__/$ENV{R_UP}/g; s/__upstream__/$ENV{R_UP_LOWER}/g; s/__UPSTREAM__/$ENV{R_UP_UPPER}/g'

# Deepest first, so a directory is renamed after what is in it
paths=$(find "$target" -depth -name '*__*__*')
for path in $paths; do
    renamed="$(dirname "$path")/$(basename "$path" | perl -pe "$substitute")"
    mv "$path" "$renamed"
done

find "$target" -type f ! -name '*.jar' -print0 | xargs -0 perl -pi -e "$substitute"
chmod +x "$target/gradlew" "$target/scripts/dev.sh"

left=$(grep -rlE '__(name|pkg|Name|Title|Upstream|upstream|UPSTREAM)__' "$target" || true)
[ -z "$left" ] || fail "placeholders left in: $left"

# The order of imports depends on the names, so ktlint sorts them. Its format tasks are cacheable, and a cache hit
# leaves the files as they are, so they must run without the cache. The spec test needs a checked-in openapi.json.
(
    cd "$target"
    ./gradlew --console=plain -q --no-build-cache --rerun-tasks ktlintFormat > /dev/null
    ./gradlew --console=plain -q ":$name-rest-server:generateOpenApiSpec" > /dev/null
)

echo "Created $target ($name, package no.kartverket.$pkg, upstream $upstream)"
echo "Next: cd $target && ./gradlew build"
