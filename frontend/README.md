# Bookstore frontend (Angular)

Simple storefront for the bookstore platform. Talks **only** to the API gateway at
`http://localhost:8080`.

## Pages

| Route | Description |
| --- | --- |
| `/login`, `/register` | Auth via `/api/auth/*` |
| `/` | Book catalog (`GET /api/books`) |
| `/books/:id` | Detail + add to cart |
| `/cart` | Client-side cart (localStorage) |
| `/checkout` | `POST /api/orders` then `POST /api/payments` (login required) |
| `/orders/:id` | Order / payment status |

## Prerequisites

Backend stack with gateway up:

```bash
# from bookstore-platform/
docker compose up -d --build
# or: source scripts/dev-env.sh && scripts/run-all.sh
curl -fsS http://localhost:8080/actuator/health
```

## Run

```bash
cd frontend
npm install
npm start
```

Open http://localhost:4200

## Smoke flow

1. Register a user (or login as `admin` / `admin12345` if bootstrapped).
2. Browse books → open one → Add to cart.
3. Cart → Checkout → Place order & pay (card last 4 defaults to `4242`).
4. Confirm order status page.

API base URL: `src/environments/environment.ts` (`apiBaseUrl`).
