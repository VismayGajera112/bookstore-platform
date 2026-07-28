# Step 10 — Containerization & Orchestration

## Docker images

Every Spring Boot module has a multi-stage Dockerfile:

1. **Build stage** (`eclipse-temurin:17-jdk-jammy`) — Maven packages `-pl <module> -am`
2. **Runtime stage** (`eclipse-temurin:17-jre-jammy`) — non-root `spring` user, curl for healthchecks

Jammy tags are multi-arch (amd64/arm64); prefer them over alpine JRE for Apple Silicon.

```bash
# From bookstore-platform/
docker build -f user-service/Dockerfile -t bookstore/user-service:1.0.0 .
# Or build everything via compose:
docker compose build
```

| Image | Port |
| --- | --- |
| `bookstore/config-server:1.0.0` | 8888 |
| `bookstore/api-gateway:1.0.0` | 8080 |
| `bookstore/user-service:1.0.0` | 8081 |
| `bookstore/book-service:1.0.0` | 8082 |
| `bookstore/order-service:1.0.0` | 8083 |
| `bookstore/payment-service:1.0.0` | 8084 |
| `bookstore/notification-service:1.0.0` | 8085 |
| `bookstore/analytics-service:1.0.0` | 8086 |

`cover-processor` stays a Lambda fat jar (Step 9), not a long-running container.

## docker compose — full local stack

```bash
docker compose up -d --build
curl -fsS http://localhost:8080/actuator/health
```

One command brings up: 6× Postgres, Kafka (+ UI), LocalStack, config-server, all domain services, and the API gateway. App containers resolve each other by service name and use `kafka:29092`. Host-published ports remain available for debugging.

## Kubernetes (minikube / kind)

```bash
# 1. Build images into the cluster's Docker daemon
minikube start
eval $(minikube docker-env)
./scripts/build-images.sh

# 2. Apply manifests
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml -f k8s/secret.yaml
kubectl apply -f k8s/infra/
kubectl apply -f k8s/

# 3. Wait and hit the gateway NodePort
kubectl -n bookstore rollout status deploy/api-gateway
minikube service api-gateway -n bookstore --url
# or: curl http://$(minikube ip):30080/actuator/health
```

### What each manifest does

| File | Purpose |
| --- | --- |
| `namespace.yaml` | `bookstore` namespace |
| `configmap.yaml` | Non-sensitive env (`CONFIG_SERVER_URL`, Kafka, AWS region, …) |
| `secret.yaml` | JWT secret, DB password, admin password (dev placeholders) |
| `*-deployment.yaml` | Pods + replicas + **liveness/readiness** → Actuator probes |
| `*-service.yaml` | ClusterIP DNS (`user-service:8081`, …) |
| `gateway-service.yaml` | NodePort `30080` front door |
| `gateway-hpa.yaml` | HPA on gateway CPU (challenge) |
| `infra/postgres.yaml` | Ephemeral per-service Postgres for demos |
| `infra/kafka.yaml` | Single-node Kafka for demos |

Probes use Spring Boot 3 health groups (`management.endpoint.health.probes.enabled=true`):

- Readiness: `/actuator/health/readiness`
- Liveness: `/actuator/health/liveness`

### Challenge — Horizontal Pod Autoscaler

`gateway-hpa.yaml` scales `api-gateway` between 2–6 replicas at 70% average CPU. Requires metrics-server:

```bash
minikube addons enable metrics-server
kubectl -n bookstore get hpa api-gateway-hpa
```

Gateway is the right HPA target: it concentrates client traffic; downstream services can be scaled independently once the front door needs more capacity.

---

## Design note — AWS EKS + IRSA

### Mapping this layout to EKS

| Local (minikube/compose) | AWS production |
| --- | --- |
| Docker images in local daemon | ECR repositories; CI pushes `:git-sha` tags |
| `Deployment` + `ClusterIP` | Same YAML; images point at ECR |
| `NodePort` gateway | AWS Load Balancer Controller → NLB/ALB `Ingress` or `Service type: LoadBalancer` |
| Postgres Deployments (emptyDir) | **Amazon RDS** (one instance or Aurora cluster per service DB) |
| In-cluster Kafka | **Amazon MSK** |
| LocalStack S3/DynamoDB/SNS | Real S3, DynamoDB, SNS; Lambda for `cover-processor` |
| ConfigMap / Secret YAML | ConfigMap + **AWS Secrets Manager / SSM** via External Secrets Operator |
| HPA on CPU | Same HPA; optionally KEDA for Kafka lag / custom metrics |

### Connecting pods to AWS APIs with IRSA

**IRSA** (IAM Roles for Service Accounts) binds an IAM role to a Kubernetes ServiceAccount so pods get temporary AWS credentials — no long-lived access keys in Secrets.

1. Create an IAM OIDC provider for the EKS cluster.
2. Create an IAM role with a trust policy that allows the OIDC subject  
   `system:serviceaccount:bookstore:book-service`.
3. Attach least-privilege policies, e.g. for book-service:
   - `s3:PutObject` / `GetObject` on `bookstore-covers`
   - `dynamodb:GetItem|PutItem|Query` on `CoverMetadata` and `UserBrowsingHistory`
4. Annotate the ServiceAccount:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: book-service
  namespace: bookstore
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT:role/bookstore-book-service
```

5. Set `serviceAccountName: book-service` on the Deployment. The AWS SDK default credential chain picks up the projected web identity token — leave `AWS_ACCESS_KEY_ID` unset.

Cover Lambda continues to use its own execution role (not IRSA). Cross-account or VPC endpoints (DynamoDB/S3 gateway endpoints) keep traffic off the public internet.

### Why not bake AWS keys into the image?

Images are immutable and often shared across environments. Credentials in ConfigMaps/Secrets or image layers leak via etcd backups, logs, and CI caches. IRSA scopes credentials to one ServiceAccount, rotates them automatically, and leaves a CloudTrail trail per assumed role.
