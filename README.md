# Bookstore Platform — Step 5 (Microservices Split)

The monolith from Steps 1–4 is now four independently deployable Spring Boot services,
each with its own PostgreSQL database. There are no shared tables and no cross-database
foreign keys — if a service needs another's data, it calls that service over HTTP.

```
bookstore-platform/
├── bookstore-common/     # shared JWT, errors, AOP logging
├── user-service/         # :8081  → user_db  :5433
├── book-service/         # :8082  → book_db  :5434
├── order-service/        # :8083  → order_db :5435   (Feign → book-service)
├── payment-service/      # :8084  → payment_db :5436 (Feign → order-service)
├── docker-compose.yml    # four Postgres instances
└── scripts/run-all.sh
```

## Quick start

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home

docker compose up -d                  # four databases
mvn install -DskipTests               # build all modules

# then start each jar (or use scripts/run-all.sh from a long-lived terminal)
java -jar user-service/target/user-service-1.0.0-SNAPSHOT.jar &
java -jar book-service/target/book-service-1.0.0-SNAPSHOT.jar &
java -jar order-service/target/order-service-1.0.0-SNAPSHOT.jar &
java -jar payment-service/target/payment-service-1.0.0-SNAPSHOT.jar &
```

| Service | Port | Database |
| --- | --- | --- |
| user-service | 8081 | `localhost:5433/user_db` |
| book-service | 8082 | `localhost:5434/book_db` |
| order-service | 8083 | `localhost:5435/order_db` |
| payment-service | 8084 | `localhost:5436/payment_db` |

Dev admin (bootstrapped by user-service): `admin` / `admin12345`.

## APIs

### user-service

| Method | Path | Role |
| --- | --- | --- |
| POST | `/api/auth/register` | PUBLIC |
| POST | `/api/auth/login` | PUBLIC |
| GET | `/api/users/me` | USER / ADMIN |
| GET | `/api/users` | ADMIN |

### book-service

Catalog reads are PUBLIC. Writes and stock reservation are authenticated (ADMIN for CRUD;
any authenticated caller for availability / reserve / release — used by order-service).

### order-service

| Method | Path | Role |
| --- | --- | --- |
| POST | `/api/orders` | USER |
| GET | `/api/orders` | USER (own) |
| GET | `/api/orders/{id}` | USER (own) / ADMIN |
| GET | `/api/orders/all` | ADMIN |
| PUT | `/api/orders/{id}/cancel` | USER (own) / ADMIN |
| PUT | `/api/orders/{id}/payment-result` | authenticated (payment-service callback) |

### payment-service

| Method | Path | Role |
| --- | --- | --- |
| POST | `/api/payments` | USER (own order) |
| GET | `/api/payments/{orderId}` | USER (own) / ADMIN |

## Why Feign + Resilience4j

`order-service` never reads `book_db`. Price and stock come from `BookClient` (OpenFeign)
with an explicit **1s connect / 2s read** timeout. Without a timeout, a hung book-service
would hang order-service threads too — that is how one outage becomes two.

`CatalogGateway` wraps every Feign call with:

- **Retry** (3 attempts, exponential backoff) for transient `CatalogUnavailableException`
- **Circuit breaker** (`bookService`): trip at 50% failures over a window of 10 calls; stay
  OPEN for 10s, then probe half-open
- **Fallback**: availability/reserve fail closed with HTTP **503** (guessing price or stock
  would oversell); release returns `false` so cancel still succeeds and compensation is
  retried later by `StockReleaseRetryJob`

Business answers (`InsufficientStockException`, `404`) are **ignored** by the breaker —
"out of stock" is book-service working correctly, not an outage.

### Verified: kill book-service → graceful degradation, no cascade

```
book-service killed
14 × POST /api/orders → HTTP 503
  "The book catalog is temporarily unavailable, so price and stock cannot be confirmed."

circuit breaker bookService:
  state: OPEN
  failureRate: 50.0%
  notPermittedCalls: 9          ← fail-fast, no outbound call attempted
  placeOrder completed in 0 ms  ← once OPEN

user-service  UP
order-service UP
payment-service UP               ← siblings unaffected
```

## JWT propagation

Every service verifies JWTs with the shared secret in `bookstore-common`
(`JwtAuthenticationFilter`). Outbound Feign calls copy the inbound `Authorization` header
via a `RequestInterceptor` in `FeignClientConfig`, so book-service sees the same principal
that called order-service. Background compensation jobs mint a short-lived service token
when there is no inbound request.

## The order saga (no distributed ACID)

Placing an order spans two services and two databases. There is no transaction that can cover
both, so each step commits locally and every step that can fail has a compensation:

```
1. ask book-service for price/stock          (read-only)
2. commit order as PENDING                   (order_db)
3. ask book-service to reserve stock         (book_db, idempotent on orderId)
4. commit AWAITING_PAYMENT                   (order_db)
── later ──
5. payment-service charges and callbacks
   SUCCESS → PAID  (reservation becomes a sale; stock stays decremented)
   FAILURE → release stock, CANCELLED
```

Eventual consistency when a remote call is down:

| Debt | Settled by |
| --- | --- |
| Stock reserved but order cancelled while book-service was down | `StockReleaseRetryJob` |
| Payment recorded but order-service missed the callback | `PaymentResultRedeliveryJob` |

Both remote operations are **idempotent**, so a retry after a lost reply is harmless.

`OrderPlaced` / `PaymentCompleted` publishers exist as log stubs for Step 7 (Kafka).

### Design choices that matter

- **PENDING before reserve**, never the reverse: a crash leaves a visible PENDING order
  holding nothing, not stock reserved for an order that never existed.
- **PAID is not cancellable** without a refund path. Releasing inventory on a paid cancel
  would leave money taken and stock restored — the databases disagreeing the wrong way.
- **No `@Transactional` around the whole saga**: that would hold a DB connection open
  across HTTP calls and still would not make the remote work atomic.

## Smoke script

```bash
# register + login
curl -X POST localhost:8081/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"carol","email":"carol@example.com","password":"s3cretpassword"}'
TOKEN=$(curl -s -X POST localhost:8081/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"carol","password":"s3cretpassword"}' | jq -r .token)

# browse (public) and order
curl "localhost:8082/api/books?size=5"
ORDER=$(curl -s -X POST localhost:8083/api/orders -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"items":[{"bookId":1,"quantity":1}]}')
ORDER_ID=$(echo "$ORDER" | jq .id)

# pay
curl -X POST localhost:8084/api/payments -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d "{\"orderId\":$ORDER_ID}"

curl localhost:8083/api/orders/$ORDER_ID -H "Authorization: Bearer $TOKEN"
```

## What this step deliberately leaves for later

- **Refunds** so a PAID order can be cancelled cleanly
- **Dockerfiles / K8s** (Step 10)
- Automated tests under `bookstore-platform/**/src/test` (the monolith suite still covers the
  earlier layers; platform smoke is currently curl-driven)

## Step 9 — S3 / Lambda / DynamoDB

See [docs/step-9-file-processing.md](docs/step-9-file-processing.md) for cover upload (S3 → Lambda →
DynamoDB → SNS) and user browsing history. LocalStack is included in `docker-compose.yml`.

## Step 10 — Containers & Kubernetes

Each service has a multi-stage `Dockerfile`. Full stack:

```bash
docker compose up -d --build    # DBs + Kafka + LocalStack + all services + gateway
curl -fsS http://localhost:8080/actuator/health
```

Kubernetes manifests live under `k8s/` (Deployments, Services, ConfigMap, Secret, probes, gateway HPA).
See [docs/step-10-kubernetes.md](docs/step-10-kubernetes.md) for minikube/kind usage and the EKS + IRSA design note.

## Step 11 — CI/CD & Monitoring

- GitHub Actions: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — build → test → GHCR images (SHA tags) → staging deploy → **production (manual approval)**
- Rollback: `./scripts/k8s-rollback.sh`
- Actuator: health public; `/actuator/prometheus` for scrapers; other actuators ADMIN
- Observability: `docker compose --profile monitoring up -d` → Prometheus `:9090`, Grafana `:3000`

Details: [docs/step-11-cicd-monitoring.md](docs/step-11-cicd-monitoring.md)

## Frontend (Angular)

Simple storefront in [`frontend/`](frontend/) — login/register, catalog, cart, checkout via the API gateway.

```bash
# gateway must be up on :8080
cd frontend && npm install && npm start
# http://localhost:4200
```

See [frontend/README.md](frontend/README.md).
