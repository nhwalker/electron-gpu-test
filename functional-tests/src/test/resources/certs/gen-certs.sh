#!/usr/bin/env bash
# Regenerates the throwaway test certificates used by TlsMtlsFunctionalTest.
#
# These are NOT secrets: a self-signed test CA, a server cert for the nginx
# sidecar (CN/SAN "web", the Docker network alias the test uses), and a client
# cert for the Electron app's mutual-TLS handshake. The generated files are
# committed so the test has no openssl-on-host dependency; re-run this only when
# they expire or the topology changes.
#
#   cd functional-tests/src/test/resources/certs && ./gen-certs.sh
set -euo pipefail
cd "$(dirname "$0")"

DAYS=3650
SUBJ_BASE="/C=US/O=electron-gpu-test"

echo "==> test CA"
openssl req -x509 -newkey rsa:2048 -nodes -days "$DAYS" \
  -keyout test-ca.key -out test-ca.crt \
  -subj "${SUBJ_BASE}/CN=electron-gpu-test test CA"

gen_leaf() { # <name> <cn> <san>
  local name="$1" cn="$2" san="$3"
  echo "==> ${name} cert (CN=${cn})"
  openssl req -newkey rsa:2048 -nodes \
    -keyout "${name}.key" -out "${name}.csr" \
    -subj "${SUBJ_BASE}/CN=${cn}"
  openssl x509 -req -in "${name}.csr" -days "$DAYS" \
    -CA test-ca.crt -CAkey test-ca.key -CAcreateserial \
    -extfile <(printf 'subjectAltName=%s\n' "$san") \
    -out "${name}.crt"
  rm -f "${name}.csr"
}

# Server cert: SAN must match the Docker network alias the Electron app dials.
gen_leaf server "web" "DNS:web"
# Client cert: presented by the Electron app for mutual TLS.
gen_leaf client "electron-gpu-test client" "DNS:electron-gpu-test-client"

rm -f test-ca.srl
echo "==> done"
ls -1 *.crt *.key
