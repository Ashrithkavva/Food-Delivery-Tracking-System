-- V1__init.sql
-- Initial schema for the order service.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    customer_id     UUID NOT NULL,
    restaurant_id   UUID NOT NULL,
    driver_id       UUID,
    status          VARCHAR(32) NOT NULL,
    subtotal_cents  BIGINT NOT NULL CHECK (subtotal_cents >= 0),
    delivery_cents  BIGINT NOT NULL CHECK (delivery_cents >= 0),
    total_cents     BIGINT NOT NULL CHECK (total_cents >= 0),
    currency        CHAR(3) NOT NULL DEFAULT 'USD',
    dropoff_lat     DOUBLE PRECISION NOT NULL,
    dropoff_lon     DOUBLE PRECISION NOT NULL,
    pickup_lat      DOUBLE PRECISION NOT NULL,
    pickup_lon      DOUBLE PRECISION NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at    TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_orders_idempotency ON orders (customer_id, idempotency_key);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_driver ON orders (driver_id) WHERE driver_id IS NOT NULL;
CREATE INDEX idx_orders_customer_created ON orders (customer_id, created_at DESC);

CREATE TABLE order_items (
    id          UUID PRIMARY KEY,
    order_id    UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sku         VARCHAR(64) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    unit_cents  BIGINT NOT NULL CHECK (unit_cents >= 0),
    notes       TEXT
);

CREATE INDEX idx_order_items_order ON order_items (order_id);

-- Transactional outbox: events written in the same transaction as the order
-- state change, then relayed to Kafka by OutboxRelay.
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at)
    WHERE published_at IS NULL;
