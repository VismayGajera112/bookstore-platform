# Centralized configuration for the bookstore platform.
#
# Served by config-server (native profile → this folder). In production, point the Config Server
# at a Git repository that contains the same files, or replace the Config Server entirely with
# Kubernetes ConfigMaps/Secrets (Step 10).
#
# Secrets (JWT_SECRET, DB_PASSWORD, ADMIN_PASSWORD) are NEVER stored here as plain values.
# Export them in the environment before starting services — see scripts/dev-env.sh.
