import { Kafka, logLevel } from 'kafkajs';

/**
 * Start a Kafka consumer subscribed to the configured topics. Each decoded
 * event is handed to `onEvent`. Returns the consumer so the caller can
 * disconnect during shutdown.
 */
export async function startKafkaConsumer({ config, logger, onEvent }) {
  const kafka = new Kafka({
    clientId: config.kafka.clientId,
    brokers: config.kafka.brokers,
    logLevel: logLevel.WARN,
    retry: { retries: 8, initialRetryTime: 300, maxRetryTime: 30_000 },
  });
  const consumer = kafka.consumer({
    groupId: config.kafka.groupId,
    sessionTimeout: config.kafka.sessionTimeoutMs,
    heartbeatInterval: 3000,
    allowAutoTopicCreation: false,
  });

  await consumer.connect();
  for (const t of config.kafka.topics) {
    await consumer.subscribe({ topic: t.trim(), fromBeginning: false });
  }

  await consumer.run({
    autoCommitInterval: 1000,
    eachMessage: async ({ topic, partition, message }) => {
      const eventType = message.headers?.['event-type']?.toString() ?? 'unknown';
      let payload;
      try {
        payload = JSON.parse(message.value.toString());
      } catch (err) {
        logger.warn({ err, topic, partition }, 'unparseable kafka message; skipping');
        return;
      }
      onEvent({
        topic,
        type: eventType,
        payload,
        receivedAt: Date.now(),
      });
    },
  });

  logger.info({ topics: config.kafka.topics, groupId: config.kafka.groupId }, 'kafka consumer running');
  return consumer;
}
