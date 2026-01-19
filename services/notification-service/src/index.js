// notification-service entry point.
//
// Architecture:
//
//   Kafka topics ──> Consumer ──> Redis Pub/Sub channel ──> All replicas
//                                                              │
//                                                              ▼
//   Customer app  <─── WebSocket  <───  Local SubscriptionHub
//
// Why Redis in the middle?
//   A given customer's WebSocket lands on one pod. The Kafka consumer reading
//   the event might land on a different pod. Redis Pub/Sub fans every event
//   out to every replica, and each replica decides whether it has a local
//   subscriber that cares.

import http from 'node:http';
import express from 'express';
import pinoHttp from 'pino-http';

import { config } from './utils/config.js';
import { logger } from './utils/logger.js';
import { metrics, register } from './utils/metrics.js';
import { startKafkaConsumer } from './kafka/consumer.js';
import { createRedis, RedisFanout } from './redis/fanout.js';
import { SubscriptionHub } from './ws/hub.js';
import { attachWebSocketServer } from './ws/server.js';
import { healthRouter } from './routes/health.js';

async function main() {
  const app = express();
  app.use(pinoHttp({ logger }));

  // Redis: two clients, one for publish/subscribe each (ioredis requires this
  // because a subscribed connection can't issue other commands).
  const pub = createRedis(config.redis);
  const sub = createRedis(config.redis);
  const fanout = new RedisFanout(pub, sub, 'fdt:notifications');

  const hub = new SubscriptionHub();
  fanout.onMessage((event) => hub.deliver(event));
  await fanout.start();

  // HTTP server + WS upgrade
  const server = http.createServer(app);
  attachWebSocketServer(server, hub, logger);

  app.use(healthRouter({ fanout }));
  app.get('/metrics', async (_req, res) => {
    res.set('Content-Type', register.contentType);
    res.end(await register.metrics());
  });

  // Kafka -> Redis fanout
  const consumer = await startKafkaConsumer({
    config,
    logger,
    onEvent: (event) => {
      metrics.eventsReceived.inc({ type: event.type });
      fanout.publish(event);
    },
  });

  server.listen(config.httpPort, () => {
    logger.info({ port: config.httpPort }, 'notification-service listening');
  });

  // Graceful shutdown
  const shutdown = async (signal) => {
    logger.info({ signal }, 'shutting down');
    server.close();
    await consumer.disconnect().catch(() => {});
    await fanout.stop().catch(() => {});
    pub.disconnect();
    sub.disconnect();
    setTimeout(() => process.exit(0), 1000).unref();
  };
  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT', () => shutdown('SIGINT'));
}

main().catch((err) => {
  logger.fatal({ err }, 'fatal error during startup');
  process.exit(1);
});
