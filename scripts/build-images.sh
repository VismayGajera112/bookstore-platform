#!/usr/bin/env bash
# Build all bookstore service images. Run from bookstore-platform/, or after
# `eval $(minikube docker-env)` so images land in the cluster's Docker daemon.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
TAG="${IMAGE_TAG:-1.0.0}"

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

for svc in "${services[@]}"; do
  echo "=== building bookstore/${svc}:${TAG} ==="
  docker build -f "${svc}/Dockerfile" -t "bookstore/${svc}:${TAG}" .
done

echo
echo "Built ${#services[@]} images tagged bookstore/*:${TAG}"
