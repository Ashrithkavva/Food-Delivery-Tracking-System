export const config = {
  httpPort: parseInt(process.env.HTTP_PORT ?? '8080', 10),
  kafka: {
    brokers: (process.env.KAFKA_BROKERS ?? 'kafka:9092').split(','),
    clientId: process.env.KAFKA_CLIENT_ID ?? 'notification-service',
    groupId: process.env.KAFKA_GROUP_ID ?? 'notification-service',
    topics: (process.env.KAFKA_TOPICS ?? 'orders.events,driver.locations').split(','),
    sessionTimeoutMs: parseInt(process.env.KAFKA_SESSION_TIMEOUT_MS ?? '30000', 10),
  },
  redis: {
    host: process.env.REDIS_HOST ?? 'redis',
    port: parseInt(process.env.REDIS_PORT ?? '6379', 10),
    password: process.env.REDIS_PASSWORD || undefined,
  },
  ws: {
    pingIntervalMs: parseInt(process.env.WS_PING_INTERVAL_MS ?? '30000', 10),
    maxPayloadBytes: parseInt(process.env.WS_MAX_PAYLOAD_BYTES ?? '8192', 10),
  },
};
