#!/usr/bin/env bash
# Rolling deploy of all bookstore images to the current kubectl context/namespace.
# Usage: ./scripts/k8s-deploy.sh ghcr.io/org/bookstore-platform <git-sha>
set -euo pipefail

IMAGE_PREFIX="${1:?image prefix required, e.g. ghcr.io/org/bookstore-platform}"
TAG="${2:?image tag / commit SHA required}"
NAMESPACE="${NAMESPACE:-bookstore}"

services=(
  config-server
  user-service
  book-service
  order-service
  payment-service
  notification-service
  analytics-service
  api-gateway
)

echo "Rolling deploy to namespace=${NAMESPACE} tag=${TAG}"
for svc in "${services[@]}"; do
  image="${IMAGE_PREFIX}/${svc}:${TAG}"
  echo "→ ${svc} = ${image}"
  kubectl -n "${NAMESPACE}" set image "deploy/${svc}" "${svc}=${image}"
done

for svc in "${services[@]}"; do
  kubectl -n "${NAMESPACE}" rollout status "deploy/${svc}" --timeout=300s
done

echo "Deploy complete."
