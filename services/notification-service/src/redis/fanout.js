import Redis from 'ioredis';

export function createRedis({ host, port, password }) {
  return new Redis({
    host,
    port,
    password,
    lazyConnect: false,
    maxRetriesPerRequest: 3,
    enableReadyCheck: true,
  });
}

/**
 * Cross-replica fan-out via Redis Pub/Sub.
 *
 * The Kafka consumer publishes each event to the shared channel; every
 * replica's subscriber receives it and invokes the local handler, which then
 * decides whether any locally-connected WebSocket wants it.
 */
export class RedisFanout {
  #pub;
  #sub;
  #channel;
  #handler = () => {};

  constructor(pub, sub, channel) {
    this.#pub = pub;
    this.#sub = sub;
    this.#channel = channel;
  }

  async start() {
    await this.#sub.subscribe(this.#channel);
    this.#sub.on('message', (_ch, raw) => {
      try {
        const event = JSON.parse(raw);
        this.#handler(event);
      } catch (_err) {
        // bad message, drop
      }
    });
  }

  publish(event) {
    this.#pub.publish(this.#channel, JSON.stringify(event));
  }

  onMessage(handler) {
    this.#handler = handler;
  }

  async stop() {
    await this.#sub.unsubscribe(this.#channel);
  }

  // Returns a probe used by readiness checks.
  isHealthy() {
    return this.#pub.status === 'ready' && this.#sub.status === 'ready';
  }
}
