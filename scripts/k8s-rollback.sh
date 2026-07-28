#!/usr/bin/env bash
# Rollback one or all Deployments to the previous ReplicaSet.
# Usage: ./scripts/k8s-rollback.sh [service-name]
set -euo pipefail

NAMESPACE="${NAMESPACE:-bookstore}"
TARGET="${1:-}"

if [[ -n "${TARGET}" ]]; then
  kubectl -n "${NAMESPACE}" rollout undo "deploy/${TARGET}"
  kubectl -n "${NAMESPACE}" rollout status "deploy/${TARGET}" --timeout=300s
  kubectl -n "${NAMESPACE}" rollout history "deploy/${TARGET}"
  exit 0
fi

for svc in config-server user-service book-service order-service payment-service \
           notification-service analytics-service api-gateway; do
  echo "Rolling back ${svc}..."
  kubectl -n "${NAMESPACE}" rollout undo "deploy/${svc}" || true
done

for svc in config-server user-service book-service order-service payment-service \
           notification-service analytics-service api-gateway; do
  kubectl -n "${NAMESPACE}" rollout status "deploy/${svc}" --timeout=300s || true
done
