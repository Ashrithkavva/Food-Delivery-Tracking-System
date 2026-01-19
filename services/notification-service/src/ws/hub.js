import { metrics } from '../utils/metrics.js';

/**
 * In-memory routing table: channel name -> Set of WebSocket clients.
 *
 * Channels we accept right now:
 *   order.{orderId}     -> events for a single order
 *   driver.{driverId}   -> location pings for a single driver
 */
export class SubscriptionHub {
  #byChannel = new Map(); // channel -> Set<ws>
  #byWs = new WeakMap();  // ws -> Set<channel>

  subscribe(ws, channel) {
    if (!this.#byChannel.has(channel)) {
      this.#byChannel.set(channel, new Set());
    }
    this.#byChannel.get(channel).add(ws);

    let channels = this.#byWs.get(ws);
    if (!channels) {
      channels = new Set();
      this.#byWs.set(ws, channels);
    }
    channels.add(channel);
    metrics.subscriptions.set(this.#totalSubs());
  }

  unsubscribe(ws, channel) {
    this.#byChannel.get(channel)?.delete(ws);
    if (this.#byChannel.get(channel)?.size === 0) {
      this.#byChannel.delete(channel);
    }
    this.#byWs.get(ws)?.delete(channel);
    metrics.subscriptions.set(this.#totalSubs());
  }

  /** Clean up everything for a closed socket. */
  removeAll(ws) {
    const channels = this.#byWs.get(ws);
    if (!channels) return;
    for (const ch of channels) {
      this.#byChannel.get(ch)?.delete(ws);
      if (this.#byChannel.get(ch)?.size === 0) {
        this.#byChannel.delete(ch);
      }
    }
    this.#byWs.delete(ws);
    metrics.subscriptions.set(this.#totalSubs());
  }

  /** Deliver an event to all sockets subscribed to its derived channel(s). */
  deliver(event) {
    const channels = channelsForEvent(event);
    if (channels.length === 0) return;

    const frame = JSON.stringify({
      type: event.type,
      topic: event.topic,
      payload: event.payload,
      ts: event.receivedAt ?? Date.now(),
    });

    for (const ch of channels) {
      const subs = this.#byChannel.get(ch);
      if (!subs || subs.size === 0) continue;
      for (const ws of subs) {
        if (ws.readyState === 1 /* OPEN */) {
          ws.send(frame);
          if (event.payload?.occurredAt) {
            metrics.deliveryLatencyMs.observe(Date.now() - Date.parse(event.payload.occurredAt));
          }
        }
      }
    }
  }

  #totalSubs() {
    let n = 0;
    for (const s of this.#byChannel.values()) n += s.size;
    return n;
  }
}

/**
 * Derive the channel(s) an event maps to. Multiple channels for one event are
 * fine: e.g. an OrderStatusChanged might fan to both order.{id} and a
 * customer-scoped channel later.
 */
function channelsForEvent(event) {
  const p = event.payload ?? {};
  switch (event.type) {
    case 'OrderCreated':
    case 'OrderStatusChanged':
    case 'OrderCancelled':
    case 'DriverAssigned':
      return p.orderId ? [`order.${p.orderId}`] : [];
    case 'DriverLocation':
      return p.driverId ? [`driver.${p.driverId}`] : [];
    default:
      return [];
  }
}
