#!/usr/bin/env bash
# Post-deploy smoke: gateway health + public catalog path.
set -euo pipefail

NAMESPACE="${NAMESPACE:-bookstore}"
GATEWAY_URL="${GATEWAY_URL:-}"

if [[ -z "${GATEWAY_URL}" ]]; then
  # Prefer port-forward for clusters without an ingress yet.
  kubectl -n "${NAMESPACE}" port-forward svc/api-gateway 18080:8080 >/tmp/bookstore-pf.log 2>&1 &
  PF_PID=$!
  trap 'kill ${PF_PID} 2>/dev/null || true' EXIT
  sleep 3
  GATEWAY_URL="http://127.0.0.1:18080"
fi

echo "Smoke against ${GATEWAY_URL}"
curl -fsS "${GATEWAY_URL}/actuator/health/readiness" | tee /tmp/bookstore-health.json
echo
curl -fsS -o /dev/null -w "GET /api/books → %{http_code}\n" "${GATEWAY_URL}/api/books?size=1"
echo "Smoke OK"
