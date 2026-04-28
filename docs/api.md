# API Reference

All HTTP APIs are versioned under `/api/v1`. Responses are JSON. Errors follow [RFC 7807 Problem Details](https://tools.ietf.org/html/rfc7807).

---

## Order Service

Base path: `/api/v1/orders`

### `POST /api/v1/orders`

Create an order. The `idempotencyKey` is scoped to `customerId`; a replay returns the original order without creating a duplicate.

```http
POST /api/v1/orders
Content-Type: application/json

{
  "customerId": "5a7b1c0e-3a1d-4e8a-9b2c-7e5d8f9a0b1c",
  "restaurantId": "12345678-1234-1234-1234-123456789012",
  "idempotencyKey": "order-2025-01-15-abc123",
  "pickupLat": 33.7490,
  "pickupLon": -84.3880,
  "dropoffLat": 33.7711,
  "dropoffLon": -84.3946,
  "deliveryCents": 599,
  "currency": "USD",
  "items": [
    { "sku": "BURGER-DBL", "name": "Double Burger", "quantity": 1, "unitCents": 1299, "notes": "no onions" },
    { "sku": "FRIES-LG",   "name": "Large Fries",   "quantity": 1, "unitCents": 499 }
  ]
}
```

Returns `201 Created` with the full `OrderResponse`.

### `GET /api/v1/orders/{id}`

Returns the order. Reads from Redis cache when available; falls back to Postgres.

### `POST /api/v1/orders/{id}/transition`

Move an order to a new status. Illegal transitions return `409 Conflict`.

```json
{ "target": "PREPARING" }
```

Valid status values: `PENDING`, `CONFIRMED`, `PREPARING`, `READY_FOR_PICKUP`, `EN_ROUTE`, `DELIVERED`, `CANCELLED`.

### `POST /api/v1/orders/{id}/driver/{driverId}`

Attach a driver to an order. Idempotent.

---

## Driver Service

Base path: `/api/v1/drivers`

### `POST /api/v1/drivers`

Onboard a new driver.

```json
{
  "fullName": "Marcus Quinn",
  "phone": "+14045551234",
  "vehicleType": "BIKE",
  "licensePlate": null
}
```

### `GET /api/v1/drivers/{id}`

Returns driver details and current status.

### `PUT /api/v1/drivers/{id}/status`

Update on-shift status. Body: `{ "status": "AVAILABLE" }`. Valid values: `OFFLINE`, `AVAILABLE`, `ON_DELIVERY`, `BREAK`.

---

## Tracking Service

Base path: `/api/v1`

### `POST /api/v1/locations`

Driver app posts a GPS ping. Returns `202 Accepted` even if the downstream Kafka publish is queued asynchronously.

```json
{
  "driverId": "...",
  "lat": 33.7490,
  "lon": -84.3880,
  "heading": 47.5,
  "speedMps": 6.2,
  "accuracyM": 8.0,
  "timestamp": "2025-01-15T14:32:18Z"
}
```

### `GET /api/v1/drivers/nearest?lat&lon&radius&limit`

Returns drivers near a point, ordered by distance. `radius` is in meters (default 2000, max 5000), `limit` is the max number of results (default 10).

```json
{
  "drivers": [
    { "driverId": "...", "distanceMeters": 143.2, "lat": 33.7491, "lon": -84.3881 }
  ],
  "count": 1
}
```

---

## Notification Service

Base path: `/ws` (WebSocket)

Connect, then subscribe:

```json
{ "op": "subscribe", "channel": "order.5a7b1c0e-3a1d-4e8a-9b2c-7e5d8f9a0b1c" }
```

Channel formats accepted: `order.<uuid>`, `driver.<uuid>`.

You'll receive event frames whenever the subscribed entity changes:

```json
{
  "type": "OrderStatusChanged",
  "topic": "orders.events",
  "payload": { "orderId": "...", "from": "PREPARING", "to": "READY_FOR_PICKUP", "occurredAt": "2025-01-15T14:34:00Z" },
  "ts": 1736951640123
}
```

Unsubscribe with `{ "op": "unsubscribe", "channel": "..." }`. The server pings every 30 seconds; if you don't pong it terminates the connection.

---

## Health & metrics

Every service exposes:

| Path                                   | Purpose                                |
|----------------------------------------|----------------------------------------|
| `/healthz` (Go, Node) or `/actuator/health/liveness` (Java) | Liveness probe |
| `/readyz`  (Go, Node) or `/actuator/health/readiness` (Java) | Readiness probe |
| `/metrics` (Go, Node) or `/actuator/prometheus` (Java) | Prometheus scrape |
