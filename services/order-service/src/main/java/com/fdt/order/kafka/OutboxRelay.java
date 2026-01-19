package com.fdt.order.kafka;

import com.fdt.order.domain.OutboxEvent;
import com.fdt.order.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Periodically drains the outbox to Kafka.
 *
 * <p>Pulls a batch of unpublished events with {@code FOR UPDATE SKIP LOCKED}
 * (configured at the repository), publishes them, and marks them published in
 * the same transaction. If publication or commit fails, the events stay
 * unpublished and will be retried on the next tick.
 *
 * <p>Multiple replicas of the service may run this relay; SKIP LOCKED keeps
 * them from publishing the same event twice.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    private final int batchSize;

    public OutboxRelay(
            OutboxEventRepository outbox,
            KafkaTemplate<String, String> kafka,
            @Value("${fdt.kafka.topics.orders-events}") String topic,
            @Value("${fdt.outbox.batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.topic = topic;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${fdt.outbox.relay-interval-ms:500}")
    @Transactional
    public void drain() {
        List<OutboxEvent> batch = outbox.findUnpublishedBatch(PageRequest.of(0, batchSize));
        if (batch.isEmpty()) return;

        for (OutboxEvent e : batch) {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    topic,
                    e.getAggregateId().toString(),  // partition key: same order -> same partition -> ordered
                    e.getPayload());
            record.headers().add(new RecordHeader("event-type",
                    e.getEventType().getBytes(StandardCharsets.UTF_8)));
            record.headers().add(new RecordHeader("aggregate-type",
                    e.getAggregateType().getBytes(StandardCharsets.UTF_8)));
            record.headers().add(new RecordHeader("event-id",
                    e.getId().toString().getBytes(StandardCharsets.UTF_8)));

            try {
                kafka.send(record).get();   // block within this txn — sync is fine at 100/batch
                e.markPublished();
            } catch (Exception ex) {
                // Roll the transaction back: leave all unpublished, retry next tick.
                log.error("Outbox publish failed for event {}", e.getId(), ex);
                throw new RuntimeException("Outbox publish failed", ex);
            }
        }
        log.debug("Relayed {} outbox events", batch.size());
    }
}
