#!/usr/bin/env bash
# Stands in for the virksomhetssertifikat: a private key for LocalKms, and a test certificate for it from a fake CA
set -euo pipefail

cd "$(dirname "$0")/.."

OUT="local-kms"
KEY="virksomhetssertifikat.key"
CERT="virksomhetssertifikat.crt"
CHAIN="virksomhetssertifikat-chain.pem"

# A new folder every run, outside local-kms/: Docker on Mac may not see a path the host just deleted and created again
mkdir -p "$OUT"
WORK=$(mktemp -d ".local-kms-work.XXXXXX")
trap 'rm -rf "$WORK"' EXIT

openssl() {
  docker run --rm -v "$PWD/$WORK":/work -w /work alpine/openssl:3.5.9 "$@"
}

for file in ca.crt ca.key "$KEY" "$CERT"; do
  if [ -f "$OUT/$file" ]; then cp "$OUT/$file" "$WORK/"; fi
done

if [ ! -f "$WORK/ca.crt" ] || [ ! -f "$WORK/ca.key" ]; then
  openssl genpkey -quiet -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out ca.key
  openssl req -x509 -new -key ca.key -out ca.crt -days 3650 \
    -subj "/C=NO/O=Lokal test/CN=Falsk CA for virksomhetssertifikat"
fi

if [ ! -f "$WORK/$KEY" ]; then
  openssl genpkey -quiet -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEY"
fi

openssl pkey -in "$KEY" -pubout -out public.pem

if [ ! -f "$WORK/$CERT" ] \
  || ! openssl verify -CAfile ca.crt "$CERT" >/dev/null 2>&1 \
  || ! openssl x509 -in "$CERT" -noout -checkend 2592000 >/dev/null \
  || [ "$(openssl x509 -in "$CERT" -noout -pubkey)" != "$(cat "$WORK/public.pem")" ]; then
  printf 'basicConstraints = critical, CA:FALSE\nkeyUsage = critical, digitalSignature, nonRepudiation\n' > "$WORK/leaf.ext"
  openssl x509 -new -force_pubkey public.pem -CA ca.crt -CAkey ca.key -days 1095 -extfile leaf.ext \
    -subj "/C=NO/O=STATENS KARTVERK/organizationIdentifier=NTRNO-971040238/serialNumber=971040238/CN=STATENS KARTVERK TEST" \
    -out "$CERT"
fi

openssl verify -CAfile ca.crt "$CERT" >/dev/null
cat "$WORK/$CERT" "$WORK/ca.crt" > "$WORK/$CHAIN"
chmod 600 "$WORK/$KEY" "$WORK/ca.key"
mv "$WORK/$KEY" "$WORK/$CERT" "$WORK/$CHAIN" "$WORK/ca.crt" "$WORK/ca.key" "$OUT/"

echo "Private key (for LocalKms):  mock-api/$OUT/$KEY"
echo "Certificate chain (for x5c): mock-api/$OUT/$CHAIN"
echo "Fake CA (for /token):        mock-api/$OUT/ca.crt"
