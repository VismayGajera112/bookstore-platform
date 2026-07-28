# Step 9 — File Processing (S3 + Lambda + DynamoDB)

This document explains the two DynamoDB-backed features, why the partition keys were chosen,
how idempotency and cost controls work, and how to run the LocalStack simulation when a real
AWS account is unavailable.

## Why serverless for cover processing?

Cover uploads are **event-driven and bursty**: most of the day nothing happens; when an admin
uploads, a short spike of work (read object → extract metadata → write row → publish) must run
once. A long-lived worker sitting idle would pay for idle capacity. Lambda charges only for
invocations and duration, scales automatically with S3 event volume, and keeps the failure
domain (a bad image) out of book-service's request path.

```
Admin → POST /api/books/{id}/cover (book-service)
      ← presigned S3 PUT URL
Admin → PUT bytes to S3 (covers/{bookId}/cover.jpg)
S3 ObjectCreated → Lambda (cover-processor)
                 → DynamoDB CoverMetadata (PK = bookId)
                 → SNS "cover for book X processed" → email subscriber
```

## Feature A — Cover upload → process → email

### Components

| Piece | Role |
| --- | --- |
| `CoverController` | ADMIN gets a presigned upload URL; PUBLIC reads processed metadata |
| S3 `bookstore-covers` | Object store; key convention `covers/{bookId}/…` |
| `CoverImageHandler` | Lambda: Head/GetObject → dimensions via ImageIO → conditional PutItem → SNS |
| DynamoDB `CoverMetadata` | PK `bookId` (Number) — one current metadata row per book |
| SNS `cover-processed` | Pub/sub closing the loop; email protocol subscription (or SES in prod) |
| SQS `cover-processor-dlq` | Dead-letter queue for failed Lambda invocations (challenge) |
| S3 lifecycle (90 days) | Cost optimization: expire old covers under `covers/` (challenge) |

### Idempotency (no duplicate rows / emails)

S3 can redeliver the same ObjectCreated event. Re-uploads change the object ETag.

The Lambda writes with:

```
conditionExpression: attribute_not_exists(bookId) OR etag <> :etag
```

- Same event again (same ETag) → condition fails → **no SNS publish**
- New upload (new ETag) → item overwritten → **one new email**

SNS is published **only after** a successful conditional put.

### Partition key justification (`CoverMetadata`)

`bookId` as the sole partition key matches the access pattern "get metadata for this book"
(`GetItem`). Cover traffic is admin-driven and low volume, so hot-partition risk is negligible.
One item per book also makes "current cover" a natural overwrite rather than an append log.

### DLQ + monitoring (challenge)

- Configure the Lambda with `DeadLetterConfig.TargetArn = cover-processor-dlq`.
- After `maximumRetryAttempts` exhausted, the event lands on the SQS DLQ.
- Monitor: CloudWatch `Errors`, `Throttles`, `DestinationDeliveryFailures`, and SQS
  `ApproximateNumberOfMessagesVisible` on the DLQ (alarm when > 0).

### Cost optimization (challenge)

S3 lifecycle rule on prefix `covers/`: expire objects after 90 days and abort incomplete
multipart uploads after 7 days. LocalStack init applies the same rule for parity.

---

## Feature B — User browsing history

### Flow

```
Authenticated GET /api/books/{id}
  → BookServiceImpl returns the book
  → @Async BrowsingHistoryService.recordViewAsync(userId, bookId)  (does not slow the read)

GET /api/books/me/history
  → Query UserBrowsingHistory WHERE userId = :me ORDER BY viewedAt DESC
  → Enrich with title/author from Postgres catalog
```

### Table design

| Attribute | Role |
| --- | --- |
| `userId` (S) | **Partition key** |
| `viewedAt` (S) | **Sort key** — `ISO-8601#bookId` (newest-first via `ScanIndexForward=false`) |
| `bookId` (N) | The book that was viewed |
| `expiresAt` (N) | Epoch seconds — DynamoDB TTL, default 30 days |

### Why `userId` as partition key?

The access pattern is always "history for **this** user." Partitioning by `userId` spreads
write load evenly across users — no single popular book becomes a hot partition. A PK of
`bookId` would concentrate every view of a bestseller onto one partition and would not
support the "my recent views" query without a GSI.

### Why DynamoDB here?

High-frequency small writes, key-based reads per user, simple access pattern, and TTL to
auto-expire stale data with **no storage cost for old history** and no cleanup job.

---

## APIs (book-service)

| Method | Path | Role | Notes |
| --- | --- | --- | --- |
| POST | `/api/books/{id}/cover` | ADMIN | Returns presigned S3 PUT URL |
| GET | `/api/books/{id}/cover` | PUBLIC | Cover URL + Lambda metadata |
| GET | `/api/books/me/history` | USER | Recently viewed, newest first |

Gateway and book-service security treat `/api/books/me/history` as authenticated (not public
catalog).

---

## LocalStack simulation

```bash
# From bookstore-platform/
docker compose up -d localstack          # runs localstack/init-aws.sh
source scripts/dev-env.sh
mvn install -DskipTests -pl cover-processor,book-service -am

# Start platform (config-server + services + gateway) as usual
scripts/run-all.sh

# Admin login → request upload URL → PUT file to LocalStack S3
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin12345"}' | jq -r .token)

UPLOAD=$(curl -s -X POST "localhost:8080/api/books/1/cover?contentType=image/jpeg" \
  -H "Authorization: Bearer $TOKEN")
echo "$UPLOAD" | jq .
# curl -X PUT "$(echo "$UPLOAD" | jq -r .uploadUrl)" -H "Content-Type: image/jpeg" --data-binary @cover.jpg

# Invoke Lambda locally against LocalStack (after packaging):
#   java -cp cover-processor/target/cover-processor-*.jar ...  or awslocal lambda invoke

# History
curl -s "localhost:8080/api/books/1" -H "Authorization: Bearer $TOKEN" >/dev/null
curl -s "localhost:8080/api/books/me/history" -H "Authorization: Bearer $TOKEN" | jq .
```

Deploying the shaded `cover-processor` jar into LocalStack Lambda and wiring the S3 event
notification is optional for day-to-day API work; you can also call `CoverImageHandler`
directly in a unit/integration test with LocalStack clients.

### Real AWS (outline)

1. Create bucket, tables, SNS topic + email/SES subscription, SQS DLQ (same shapes as init script).
2. Package `cover-processor` with `maven-shade-plugin`; deploy as Java 17 Lambda.
3. S3 event notification → Lambda; Lambda env: `COVER_METADATA_TABLE`, `COVER_PROCESSED_TOPIC_ARN`.
4. Attach DLQ; set reserved concurrency if needed; enable CloudWatch alarms.
5. Point book-service at real AWS (`bookstore.aws.endpoint` empty) with an IAM role that can
   `s3:PutObject` (presign), `dynamodb:GetItem/PutItem/Query` on the two tables.

---

## Module layout

```
cover-processor/          # Lambda fat jar
  …/CoverImageHandler.java
book-service/
  …/controller/CoverController.java
  …/controller/HistoryController.java
  …/service/CoverService.java
  …/service/BrowsingHistoryService.java
  …/config/AwsClientConfig.java
localstack/init-aws.sh    # bucket, tables, SNS, DLQ, lifecycle
docs/step-9-file-processing.md
```
