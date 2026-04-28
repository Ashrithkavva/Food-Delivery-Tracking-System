# Food Delivery Tracking System

A polyglot, event-driven microservices platform that ingests live driver telemetry, manages the order lifecycle, and streams sub-second updates to customers — the kind of architecture you'd find behind real-world delivery products like DoorDash, Uber Eats, or Wolt.

The system is designed for **AWS EKS**, uses **Kafka** as its event spine, **Redis** as a hot-path cache + geo-index, and **PostgreSQL** as the system of record. Services are written in the language best suited to the job: **Spring Boot (Java)** for order/driver domain logic, **Go** for the high-throughput location pipeline, and **Node.js** for the WebSocket fan-out layer.

---

## Why this design

A delivery platform has three workloads that pull in opposite directions, and the architecture reflects that:

1. **Transactional order management** — needs strong consistency, ACID writes, mature ORM tooling. → **Spring Boot + PostgreSQL.**
2. **High-frequency telemetry ingest** — drivers emit GPS pings every 2–5 seconds. At 100k active drivers that's 20–50k writes/sec, mostly write-dominated, no joins. → **Go + Redis GEO + Kafka.**
3. **Real-time client fan-out** — thousands of customer apps each subscribed to one order's updates, mostly idle connections. → **Node.js + WebSockets + Redis Pub/Sub.**

Kafka decouples the three. Each service reads what it needs and writes what it produces. No service calls another synchronously on the hot path.

---

## Architecture

```
                            ┌──────────────────┐
                            │  Customer Apps   │
                            │ (iOS / Android / │
                            │       Web)       │
                            └────────┬─────────┘
                                     │ HTTPS / WSS
                          ┌──────────▼───────────┐
                          │    AWS ALB / NGINX   │
                          │   (K8s Ingress)      │
                          └──┬───────┬────────┬──┘
              ┌──────────────┘       │        └────────────────┐
              │                      │                         │
   ┌──────────▼─────────┐  ┌─────────▼────────┐  ┌─────────────▼────────┐
   │   Order Service    │  │  Driver Service  │  │ Notification Service │
   │   (Spring Boot)    │  │  (Spring Boot)   │  │      (Node.js)       │
   │                    │  │                  │  │                      │
   │ • Order lifecycle  │  │ • Driver roster  │  │ • WebSocket gateway  │
   │ • Pricing snapshot │  │ • Availability   │  │ • Per-order channel  │
   │ • Idempotent POST  │  │ • Assignment     │  │ • Redis pub/sub      │
   └──┬──────────────┬──┘  └──┬───────────────┘  └────────┬─────────────┘
      │              │        │                            ▲
      │              │        │                            │
   ┌──▼────┐    ┌────▼────────▼──┐                         │
   │  RDS  │    │   Apache Kafka │◄────────────────────────┘
   │ Postgres   │  (MSK on AWS)  │
   └───────┘    │                │
                │ Topics:        │
                │ • orders.events│
                │ • driver.locs  │
                │ • notify.out   │
                └──▲────────┬────┘
                   │        │
                   │   ┌────▼─────────────────────┐
                   │   │   Tracking Service       │
                   │   │         (Go)             │
                   │   │                          │
                   │   │ • HTTP/gRPC location API │
                   │   │ • Kafka consumer+producer│
                   │   │ • Redis GEOADD writer    │
                   │   │ • Nearest-driver query   │
                   └───┴────────────┬─────────────┘
                                    │
                              ┌─────▼────────┐
                              │ ElastiCache  │
                              │    Redis     │
                              │ • Geo index  │
                              │ • Hot orders │
                              │ • Pub/Sub    │
                              └──────────────┘
```

Mermaid version of the same diagram lives in [`docs/architecture.md`](docs/architecture.md).

---

## Services at a glance

| Service                 | Lang        | Responsibility                                              | Stores               |
|-------------------------|-------------|-------------------------------------------------------------|----------------------|
| `order-service`         | Java 21 / Spring Boot 3 | Order CRUD, lifecycle state machine, event emission       | Postgres, Redis cache |
| `driver-service`        | Java 21 / Spring Boot 3 | Driver registry, on-shift state, nearest-driver assignment | Postgres             |
| `tracking-service`      | Go 1.22     | Ingest driver GPS, write to Redis GEO, broadcast to Kafka  | Redis, Kafka          |
| `notification-service`  | Node.js 20  | WebSocket gateway, per-order subscriptions, push notifications | Redis pub/sub     |

---

## Event flow: a customer places an order

1. Customer POSTs `/api/v1/orders` to **order-service**. Body is validated, an idempotency key prevents double-charges, and the order is written to Postgres in a transaction.
2. Inside the same transaction, an `OrderCreated` event is written to the outbox table. A scheduled relay publishes it to the `orders.events` Kafka topic. (Transactional outbox = no dropped events even on crash.)
3. **driver-service** consumes `OrderCreated`, queries the geo-index for the nearest available driver via **tracking-service**'s gRPC API, marks them as assigned, and emits `DriverAssigned`.
4. **notification-service** consumes both events from Kafka and pushes them to any WebSocket clients subscribed to that order's channel. The customer's screen now shows "Assigning driver…" → "Marcus is heading to the restaurant."
5. The driver's phone POSTs `/locations` to **tracking-service** every 3 seconds. Each ping does a single `GEOADD` to Redis and is published to `driver.locations` Kafka. **notification-service** consumes those and pushes interpolated map deltas to the customer's WebSocket.
6. State transitions (`PickedUp`, `Delivered`) flow back through Kafka the same way.

End-to-end latency from driver ping → customer screen update target: **< 500 ms p95**.

---

## Getting started

```bash
# 1. Bring up data plane (Kafka, Redis, Postgres)
kubectl apply -k infrastructure/kubernetes/data-plane

# 2. Apply schemas (Flyway runs on Spring Boot startup; for psql:)
psql -h localhost -U fdt -f db/migrations/V1__init.sql

# 3. Deploy services
kubectl apply -k infrastructure/kubernetes/services

# 4. Get the ingress URL
kubectl get ingress fdt-ingress
```

For a full AWS deployment, see [`infrastructure/terraform/README.md`](infrastructure/terraform/README.md) — it provisions an EKS cluster, an RDS Postgres instance, an MSK Kafka cluster, and an ElastiCache Redis replication group.

Run each service locally without Kubernetes:

```bash
# Order service
cd services/order-service && ./mvnw spring-boot:run

# Driver service
cd services/driver-service && ./mvnw spring-boot:run

# Tracking service
cd services/tracking-service && go run ./cmd/tracking

# Notification service
cd services/notification-service && npm install && npm start
```

---

## Repository layout

```
food-delivery-tracker/
├── services/
│   ├── order-service/          Spring Boot, Java 21
│   ├── driver-service/         Spring Boot, Java 21
│   ├── tracking-service/       Go 1.22
│   └── notification-service/   Node.js 20
├── infrastructure/
│   ├── kubernetes/             Kustomize overlays (base, data-plane, services)
│   └── terraform/              EKS, VPC, MSK, RDS, ElastiCache
├── db/migrations/              Cross-service SQL reference
├── docs/                       Architecture, API, deployment, runbooks
└── .github/workflows/          CI: build, test, lint, image push
```

---

## Highlights for the curious reader

- **Transactional outbox** in `order-service` so Kafka events and DB writes can never diverge ([`OutboxRelay.java`](services/order-service/src/main/java/com/fdt/order/kafka/OutboxRelay.java)).
- **Redis geo-index** in `tracking-service` — `GEOADD` + `GEOSEARCH` gives O(log N) nearest-driver lookups against millions of pings.
- **WebSocket fan-out across replicas** via Redis Pub/Sub so any `notification-service` pod can deliver to any connected client.
- **Health, readiness, and liveness** probes on every service. Kafka consumer lag exposed as a custom readiness check on `notification-service`.
- **Prometheus metrics** scraped from every pod; consumer lag, p99 latency, in-flight orders, geo-cell hit rate.
- **Backpressure** on the Go ingest path via a bounded channel — drops oldest first when downstream Kafka stalls, preferring liveness of the freshest pings.

---

## License

MIT. Use it as a portfolio reference, fork it, take it apart.
