#!/bin/sh
set -eu

VAULT_CONFIG_IMPORT_ENABLED="${VAULT_CONFIG_IMPORT_ENABLED:-true}"

case "$VAULT_CONFIG_IMPORT_ENABLED" in
  false|FALSE|0|no|NO|off|OFF)
    VAULT_CONFIG_IMPORT_ENABLED=false
    ;;
  *)
    VAULT_CONFIG_IMPORT_ENABLED=true
    ;;
esac

if [ "$VAULT_CONFIG_IMPORT_ENABLED" = "true" ]; then
  VAULT_HOST="${VAULT_HOST:-localhost}"

  if [ -z "${VAULT_TOKEN:-}" ] && [ -n "${VAULT_TOKEN_FILE:-}" ]; then
    if [ ! -r "$VAULT_TOKEN_FILE" ]; then
      echo "VAULT_TOKEN_FILE is not readable: $VAULT_TOKEN_FILE" >&2
      exit 1
    fi
    VAULT_TOKEN="$(cat "$VAULT_TOKEN_FILE")"
    export VAULT_TOKEN
  fi

  if [ -z "${VAULT_TOKEN:-}" ]; then
    echo "VAULT_TOKEN or VAULT_TOKEN_FILE is required when Vault config import is enabled" >&2
    exit 1
  fi

  VAULT_SECRET="${VAULT_SECRET:-PredictionsBot}"
  VAULT_PORT="${VAULT_PORT:-8200}"
  VAULT_KV_VERSION="${VAULT_KV_VERSION:-V2}"
  VAULT_SECRET_ENGINE_NAME="${VAULT_SECRET_ENGINE_NAME:-secret}"
  VAULT_FAIL_FAST="${VAULT_FAIL_FAST:-true}"
  VAULT_CONNECT_TIMEOUT="${VAULT_CONNECT_TIMEOUT:-5s}"
  VAULT_READ_TIMEOUT="${VAULT_READ_TIMEOUT:-10s}"
  VAULT_RETRY_ATTEMPTS="${VAULT_RETRY_ATTEMPTS:-1}"
  VAULT_RETRY_DELAY="${VAULT_RETRY_DELAY:-1s}"
  export VAULT_SECRET VAULT_PORT VAULT_KV_VERSION VAULT_SECRET_ENGINE_NAME VAULT_FAIL_FAST
  export VAULT_CONNECT_TIMEOUT VAULT_READ_TIMEOUT VAULT_RETRY_ATTEMPTS VAULT_RETRY_DELAY

  VAULT_IMPORT_FILE="${VAULT_IMPORT_FILE:-/tmp/micronaut-vault-import.properties}"
  VAULT_IMPORT_LOCATION="vault://${VAULT_HOST}:${VAULT_PORT}/${VAULT_SECRET}?token=${VAULT_TOKEN}&kv-version=${VAULT_KV_VERSION}&secret-engine-name=${VAULT_SECRET_ENGINE_NAME}&fail-fast=${VAULT_FAIL_FAST}&connect-timeout=${VAULT_CONNECT_TIMEOUT}&read-timeout=${VAULT_READ_TIMEOUT}&retry-attempts=${VAULT_RETRY_ATTEMPTS}&retry-delay=${VAULT_RETRY_DELAY}"
  {
    printf '%s=%s\n' "micronaut.config.import" "$VAULT_IMPORT_LOCATION"
  } > "$VAULT_IMPORT_FILE"

  if [ "$(id -u)" = "0" ]; then
    chown spring:spring "$VAULT_IMPORT_FILE"
  fi
  chmod 0400 "$VAULT_IMPORT_FILE"

  MICRONAUT_CONFIG_FILES="${MICRONAUT_CONFIG_FILES:+$MICRONAUT_CONFIG_FILES,}$VAULT_IMPORT_FILE"
  export MICRONAUT_CONFIG_FILES

  echo "Vault config import enabled: http://${VAULT_HOST}:${VAULT_PORT}/${VAULT_SECRET}"
fi

if [ "$(id -u)" = "0" ]; then
  exec setpriv --reuid=spring --regid=spring --init-groups "$@"
fi

exec "$@"
