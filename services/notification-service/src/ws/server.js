import { WebSocketServer } from 'ws';
import { config } from '../utils/config.js';
import { metrics } from '../utils/metrics.js';

/**
 * Wire a WebSocket server to the given HTTP server. Clients send JSON frames:
 *
 *   { "op": "subscribe", "channel": "order.<uuid>" }
 *   { "op": "unsubscribe", "channel": "order.<uuid>" }
 *
 * Server sends event frames:
 *
 *   { "type": "OrderStatusChanged", "topic": "orders.events", "payload": {...}, "ts": 1700000000000 }
 *
 * A ping/pong heartbeat reaps zombie connections every 30s.
 */
export function attachWebSocketServer(httpServer, hub, logger) {
  const wss = new WebSocketServer({
    server: httpServer,
    path: '/ws',
    maxPayload: config.ws.maxPayloadBytes,
  });

  wss.on('connection', (ws, req) => {
    ws.isAlive = true;
    metrics.wsConnections.inc();
    logger.debug({ ip: req.socket.remoteAddress }, 'ws connected');

    ws.on('pong', () => { ws.isAlive = true; });

    ws.on('message', (raw) => {
      let msg;
      try { msg = JSON.parse(raw.toString()); }
      catch { return ws.send(JSON.stringify({ error: 'invalid json' })); }

      const channel = String(msg.channel ?? '');
      if (!isValidChannel(channel)) {
        return ws.send(JSON.stringify({ error: 'invalid channel' }));
      }
      switch (msg.op) {
        case 'subscribe':
          hub.subscribe(ws, channel);
          ws.send(JSON.stringify({ ok: true, op: 'subscribe', channel }));
          break;
        case 'unsubscribe':
          hub.unsubscribe(ws, channel);
          ws.send(JSON.stringify({ ok: true, op: 'unsubscribe', channel }));
          break;
        default:
          ws.send(JSON.stringify({ error: 'unknown op' }));
      }
    });

    ws.on('close', () => {
      hub.removeAll(ws);
      metrics.wsConnections.dec();
    });
  });

  // Heartbeat: ping every interval, reap if no pong received since last tick.
  const interval = setInterval(() => {
    for (const ws of wss.clients) {
      if (!ws.isAlive) { ws.terminate(); continue; }
      ws.isAlive = false;
      ws.ping();
    }
  }, config.ws.pingIntervalMs);

  wss.on('close', () => clearInterval(interval));
  return wss;
}

function isValidChannel(ch) {
  // Allow only the namespaces we serve, and prevent unbounded scan keys.
  return /^(order|driver)\.[A-Za-z0-9-]{1,64}$/.test(ch);
}
