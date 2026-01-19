import client from 'prom-client';

const register = new client.Registry();
client.collectDefaultMetrics({ register, prefix: 'notify_' });

const metrics = {
  eventsReceived: new client.Counter({
    name: 'notify_events_received_total',
    help: 'Events received from Kafka, by type',
    labelNames: ['type'],
    registers: [register],
  }),
  wsConnections: new client.Gauge({
    name: 'notify_ws_connections',
    help: 'Currently open WebSocket connections',
    registers: [register],
  }),
  subscriptions: new client.Gauge({
    name: 'notify_subscriptions',
    help: 'Active per-channel subscriptions',
    registers: [register],
  }),
  deliveryLatencyMs: new client.Histogram({
    name: 'notify_delivery_latency_ms',
    help: 'Event occurredAt -> ws.send latency in ms',
    buckets: [10, 25, 50, 100, 250, 500, 1000, 2500, 5000],
    registers: [register],
  }),
};

export { metrics, register };
