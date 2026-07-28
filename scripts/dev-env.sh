#!/usr/bin/env bash
# Dev secrets and paths for the bookstore platform. Source this before starting services:
#   source scripts/dev-env.sh
#
# These values match the Step 5 local defaults. Never use them outside a laptop.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Always pin to this repo's config-repo (do not inherit a stale CONFIG_REPO_PATH).
export CONFIG_REPO_PATH="$ROOT/config-repo"
export CONFIG_SERVER_URL="${CONFIG_SERVER_URL:-http://localhost:8888}"

# Shared across every service — must match what user-service uses to sign tokens.
export JWT_SECRET="${JWT_SECRET:-change-me-in-production-this-is-a-dev-only-secret-key-32b}"
export JWT_EXPIRATION="${JWT_EXPIRATION:-1h}"

# Database password for all four Postgres instances (compose defaults).
export DB_USERNAME="${DB_USERNAME:-postgres}"
export DB_PASSWORD="${DB_PASSWORD:-postgres}"

# Bootstrap ADMIN for user-service (self-registration only creates USER accounts).
export ADMIN_BOOTSTRAP_ENABLED="${ADMIN_BOOTSTRAP_ENABLED:-true}"
export ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
export ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin12345}"
export ADMIN_EMAIL="${ADMIN_EMAIL:-admin@bookstore.local}"

echo "CONFIG_REPO_PATH=$CONFIG_REPO_PATH"
echo "CONFIG_SERVER_URL=$CONFIG_SERVER_URL"
echo "JWT_SECRET is set (${#JWT_SECRET} chars); DB_PASSWORD and ADMIN_PASSWORD are set from env"
