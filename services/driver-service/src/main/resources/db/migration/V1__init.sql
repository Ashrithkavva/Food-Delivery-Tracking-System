CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE drivers (
    id              UUID PRIMARY KEY,
    full_name       VARCHAR(255) NOT NULL,
    phone           VARCHAR(32) NOT NULL UNIQUE,
    vehicle_type    VARCHAR(32) NOT NULL,
    license_plate   VARCHAR(32),
    status          VARCHAR(32) NOT NULL DEFAULT 'OFFLINE',
    rating          NUMERIC(3,2) DEFAULT 5.00 CHECK (rating BETWEEN 0 AND 5),
    onboarded_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_status_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_drivers_status ON drivers (status);

CREATE TABLE driver_assignments (
    id           UUID PRIMARY KEY,
    driver_id    UUID NOT NULL REFERENCES drivers(id),
    order_id     UUID NOT NULL,
    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    UNIQUE (order_id)
);

CREATE INDEX idx_assignments_driver ON driver_assignments (driver_id) WHERE completed_at IS NULL;
