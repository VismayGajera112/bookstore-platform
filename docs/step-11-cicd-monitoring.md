# Step 11 — CI/CD & Monitoring

## CI/CD pipeline

Workflow: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)

| Stage | Trigger | What happens |
| --- | --- | --- |
| **Build & test** | every push / PR | `mvn -B verify` — a failing test **fails the pipeline** |
| **Build & push images** | push to `main` only | multi-stage Docker build per service → **GHCR** tagged with full SHA, short SHA, and `latest` |
| **Deploy staging** | after images | `kubectl set image` (or dry-run plan if `STAGING_KUBECONFIG` unset) |
| **Deploy production** | after staging | GitHub Environment **`production`** = **manual approval gate**, then rolling deploy + smoke |

### Secrets / environments to configure in GitHub

| Name | Purpose |
| --- | --- |
| Environment `staging` | optional URL; holds `STAGING_KUBECONFIG` (base64 kubeconfig) |
| Environment `production` | **required reviewers**; holds `PROD_KUBECONFIG` |
| `GITHUB_TOKEN` | automatic — pushes to `ghcr.io/<owner>/<repo>/<service>` |

### Rollback

Default strategy is Kubernetes **rolling update** (Deployment revision history):

```bash
./scripts/k8s-rollback.sh api-gateway          # one service
./scripts/k8s-rollback.sh                      # all services
kubectl -n bookstore rollout history deploy/api-gateway
```

Deploy a known-good SHA:

```bash
./scripts/k8s-deploy.sh ghcr.io/<owner>/bookstore-platform <previous-sha>
./scripts/k8s-smoke.sh
```

### Challenge — blue-green / canary

| Strategy | How on this platform |
| --- | --- |
| **Rolling** (default) | `kubectl set image` / CI deploy job; maxUnavailable controlled by Deployment |
| **Blue-green** | two Deployments (`api-gateway-blue` / `-green`); flip Service selector when smoke passes |
| **Canary** | example manifest [`k8s/canary/gateway-canary.yaml`](../k8s/canary/gateway-canary.yaml); send ~10% traffic to canary Service via Ingress/mesh weights; promote by updating stable image and deleting canary; abort by deleting canary only |

Watch canary with Grafana **error rate** and **p99** panels before promoting.

---

## Actuator (health + metrics)

Already on every Spring Boot service. Shared config (`config-repo/application.yml`):

- Exposed: `health`, `info`, `metrics`, **`prometheus`**, `refresh`, `env`
- Probes: `/actuator/health/liveness`, `/actuator/health/readiness`
- Security:
  - **Public:** `health/**`, `info`, `prometheus` (scrapers; lock down with NetworkPolicy in prod)
  - **ADMIN JWT:** everything else under `/actuator/**` (`refresh`, `env`, `metrics` JSON, circuit breakers)

Micrometer Prometheus registry is on every service POM → scrape path `/actuator/prometheus`.

---

## Metrics to monitor (and what to alarm)

| Metric | Source | Why | Alarm? |
| --- | --- | --- | --- |
| **Request rate** | `http.server.requests` (rate) | traffic / capacity | no (context only) |
| **Error rate (5xx)** | `http.server.requests{status=~5..}` | user-visible failures | **yes** — >5% for 5m → SNS/Slack critical |
| **p99 latency** | histogram quantile on `http.server.requests` | SLO latency | **yes** — >2s for 10m → warning |
| **Kafka consumer lag** | broker/lag exporter or group lag | backlog / stuck consumer | **yes** — lag > N for 10m |
| **Kafka DLT depth** | `kafka.dlt.depth` (notification-service) | poison messages | **yes** — depth > 10 |
| **DB connections** | `hikaricp.connections.*` | pool saturation | **yes** — pending > 0 for 5m |
| **Circuit breaker open** | Resilience4j meters (order/payment) | cascading failure | **yes** — state OPEN |
| **Instance up** | Prometheus `up` | process/scrape dead | **yes** — down 2m |
| **JVM heap / GC** | Micrometer JVM binders | memory pressure | warning only |

Alert rules live in [`monitoring/alerts.yml`](../monitoring/alerts.yml). Wire Alertmanager → **SNS topic** or **Slack webhook** for paging.

Per-service focus:

| Service | Extra signals |
| --- | --- |
| api-gateway | auth rejection rate (401/403), route latency |
| user-service | login failures, JWT issue rate |
| book-service | stock reservation conflicts (409), DynamoDB/S3 errors |
| order-service | saga compensations, CB `bookService` |
| payment-service | payment failure ratio, CB `orderService` |
| notification / analytics | consumer lag, DLT depth, idempotent skip rate |
| config-server | scrape failures (clients can't boot) |

---

## Dashboard (challenge)

```bash
# Full stack already up, then:
docker compose --profile monitoring up -d
# Grafana http://localhost:3000  (admin / admin)
# Prometheus http://localhost:9090
```

Provisioned dashboard: **Bookstore Platform Overview** (request rate, error rate, p99, HikariCP).

On EKS: same Micrometer endpoints + Prometheus Operator `ServiceMonitor`, or CloudWatch Agent / ADOT scraping `/actuator/prometheus` and alarming in CloudWatch → SNS.

---

## Definition of Done checklist

- [x] Every commit/PR runs automated build + test
- [x] Pipeline produces versioned images (`:sha`, `:latest` on main)
- [x] Deploy designed with staging + **manual prod approval** + **rollback scripts**
- [x] Health endpoints exposed; sensitive actuators ADMIN-secured; Prometheus scrape path available
- [x] Metrics + alarms defined; Grafana dashboard + Prometheus alerts checked in
