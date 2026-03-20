#!/usr/bin/env bash
# Seed local data so you can poke the API end-to-end.
# Assumes order-service is on :8081, driver-service on :8082, tracking on :8083.

set -euo pipefail

ORDER_URL="${ORDER_URL:-http://localhost:8081}"
DRIVER_URL="${DRIVER_URL:-http://localhost:8082}"
TRACKING_URL="${TRACKING_URL:-http://localhost:8083}"

echo "→ Onboarding 3 drivers..."
for name in "Alex Rivera" "Marcus Quinn" "Priya Shah"; do
  curl -s -X POST "$DRIVER_URL/api/v1/drivers" \
    -H 'Content-Type: application/json' \
    -d "{\"fullName\":\"$name\",\"phone\":\"+1404555$RANDOM\",\"vehicleType\":\"BIKE\"}" | jq -r '.id'
done

echo "→ Posting 3 driver locations near downtown Atlanta..."
# Use real UUIDs; for the demo we just generate them.
for i in 1 2 3; do
  uuid=$(uuidgen | tr '[:upper:]' '[:lower:]')
  curl -s -X POST "$TRACKING_URL/api/v1/locations" \
    -H 'Content-Type: application/json' \
    -d "{\"driverId\":\"$uuid\",\"lat\":33.749$i,\"lon\":-84.388$i,\"timestamp\":\"$(date -u +%FT%TZ)\"}"
  echo "  posted $uuid"
done

echo "→ Creating a sample order..."
curl -s -X POST "$ORDER_URL/api/v1/orders" \
  -H 'Content-Type: application/json' \
  -d @docs/sample-order.json | jq

echo "→ Querying nearest drivers to pickup point..."
curl -s "$TRACKING_URL/api/v1/drivers/nearest?lat=33.7490&lon=-84.3880&radius=3000" | jq

echo "Done."
