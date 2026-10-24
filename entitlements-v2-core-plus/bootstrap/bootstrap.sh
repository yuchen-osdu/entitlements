#!/usr/bin/env bash

set -euo pipefail

log() {
  echo "[$(date -Iseconds)] $*"
}

fail() {
  echo "[$(date -Iseconds)] ERROR: $*" >&2
  exit 1
}

wait_for_entitlements() {
  log "Waiting for Entitlements to become reachable..."

  local max_retries=60
  local delay=3

  for ((i=1; i<=max_retries; i++)); do
    if curl -sf \
      --connect-timeout 5 --max-time 15 \
      "${ENTITLEMENTS_HOST}/api/entitlements/v2/info" >/dev/null; then

      log "✅ Entitlements endpoint reachable"
      sleep 5
      return 0
    fi

    log "Entitlements not reachable yet ($i/$max_retries)..."
    sleep $delay
  done

  fail "Entitlements did not become reachable in time"
}

get_access_token() {
  log "Requesting access token from Keycloak..." >&2

  local token
  token=$(curl -s --location \
    --connect-timeout 5 --max-time 15 \
    "${OPENID_PROVIDER_URL}/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=client_credentials" \
    --data-urlencode "scope=openid" \
    --data-urlencode "client_id=${OPENID_PROVIDER_CLIENT_ID}" \
    --data-urlencode "client_secret=${OPENID_PROVIDER_CLIENT_SECRET}" \
    | jq -r ".access_token")

  [[ -z "$token" || "$token" == "null" ]] && fail "Failed to obtain access token"

  echo "$token"
}

prepare_payload() {
  log "Preparing provisioning payload..."

  cat <<EOF >/opt/configuration.json
{
  "aliasMappings": [
    { "aliasId": "SERVICE_PRINCIPAL", "userId": "$ADMIN_USER_EMAIL" },
    { "aliasId": "SERVICE_PRINCIPAL_AIRFLOW", "userId": "$AIRFLOW_COMPOSER_EMAIL" },
    { "aliasId": "SERVICE_PRINCIPAL_INDEXER", "userId": "indexer@service.local" },
    { "aliasId": "SERVICE_PRINCIPAL_NOTIFICATION", "userId": "notification@service.local" },
    { "aliasId": "SERVICE_PRINCIPAL_STORAGE", "userId": "storage@service.local" },
    { "aliasId": "SERVICE_PRINCIPAL_SEISMIC", "userId": "seismic@service.local" },
    { "aliasId": "SERVICE_PRINCIPAL_REGISTER", "userId": "register@service.local" },
    { "aliasId": "SERVICE_PRINCIPAL_GCZ", "userId": "gcz@service.local" },
    { "aliasId": "SERVICE_PRINCIPAL_WORKFLOW", "userId": "workflow@service.local" },
    { "aliasId": "SERVICE_PRINCIPAL_RAFS_DDMS", "userId": "rafs-ddms@service.local" }
  ]
}
EOF
}

call_entitlements() {
  local token=$1

  curl -s --location --request POST \
    --connect-timeout 5 --max-time 15 \
    "${ENTITLEMENTS_HOST}/api/entitlements/v2/tenant-provisioning" \
    --header "Content-Type: application/json" \
    --header "data-partition-id: ${DATA_PARTITION_ID}" \
    --header "Authorization: Bearer ${token}" \
    --data @/opt/configuration.json \
    --write-out "%{http_code}" \
    --output /tmp/entitlements_output.json
}

wait_for_jwks() {
  log "Priming Istio JWKS cache..."

  local max_retries=20
  local delay=5

  for ((i=1; i<=max_retries; i++)); do
    local status_code
    status_code=$(curl -s -o /dev/null -w "%{http_code}" \
      --connect-timeout 5 --max-time 15 \
      "${ENTITLEMENTS_HOST}/api/entitlements/v2/groups" \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H "data-partition-id: ${DATA_PARTITION_ID}")

    # 200 or 404 means Istio accepted the JWT (JWKS cache is warm)
    if [[ "$status_code" == "200" || "$status_code" == "404" ]]; then
      log "✅ Istio JWKS cache primed (HTTP $status_code)"
      return 0
    fi

    log "JWKS not ready yet (HTTP $status_code, attempt $i/$max_retries), retrying in ${delay}s..."
    sleep $delay
  done

  fail "Istio JWKS cache did not prime in time"
}

check_existing() {
  log "Checking if tenant already initialized..."

  for i in {1..5}; do
    if curl -sf \
      --connect-timeout 5 --max-time 15 \
      "${ENTITLEMENTS_HOST}/api/entitlements/v2/groups" \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H "data-partition-id: ${DATA_PARTITION_ID}" \
    | grep -q "users.datalake.admins"; then

      log "⚠️ Tenant already initialized — re-running provisioning to apply any new groups"
      return 0
    fi

    log "Check attempt $i/5 failed, retrying..."
    sleep 2
  done
}

bootstrap_entitlements() {
  local max_retries=30
  local delay=5

  for ((i=1; i<=max_retries; i++)); do
    log "Attempt $i/$max_retries: provisioning tenant..."

    status_code=$(call_entitlements "$ACCESS_TOKEN")

    # ✅ success
    if [[ "$status_code" == "200" ]]; then
      log "✅ Provisioning completed successfully"
      return 0
    fi

    # ✅ treat 400 as already provisioned (safe after check_existing)
    if [[ "$status_code" == "400" ]]; then
      log "⚠️ Received 400 (likely already provisioned)"
      return 0
    fi

    log "⚠️ Failed with status ${status_code}"

    if [[ -s /tmp/entitlements_output.json ]]; then
      log "Response:"
      cat /tmp/entitlements_output.json || true
    fi

    if [[ $i -lt $max_retries ]]; then
      log "Retrying in ${delay}s..."
      sleep $delay
    fi
  done

  fail "Provisioning failed after ${max_retries} attempts"
}

# --- MAIN ---

: "${DATA_PARTITION_ID:?missing}"
: "${ENTITLEMENTS_HOST:?missing}"
: "${OPENID_PROVIDER_URL:?missing}"
: "${OPENID_PROVIDER_CLIENT_ID:?missing}"
: "${OPENID_PROVIDER_CLIENT_SECRET:?missing}"
: "${ADMIN_USER_EMAIL:?missing}"
: "${AIRFLOW_COMPOSER_EMAIL:?missing}"

log "Starting entitlements bootstrap..."

wait_for_entitlements

ACCESS_TOKEN=$(get_access_token)
export ACCESS_TOKEN

prepare_payload

wait_for_jwks

check_existing

bootstrap_entitlements

touch /tmp/bootstrap_ready
log "✅ Bootstrap finished successfully"

sleep infinity
