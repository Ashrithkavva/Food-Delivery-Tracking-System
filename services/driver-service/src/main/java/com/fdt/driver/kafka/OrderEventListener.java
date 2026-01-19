package com.fdt.driver.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fdt.driver.service.DriverService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Listens to orders.events. When it sees an OrderCreated, it asks the driver
 * service to assign a nearby driver. All other event types are acknowledged
 * and ignored (we share a topic with other consumers).
 *
 * <p>Acknowledgement is manual: we only ack after the assignment attempt
 * completes, so a crash mid-processing replays the message on restart.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ObjectMapper mapper;
    private final DriverService drivers;

    public OrderEventListener(ObjectMapper mapper, DriverService drivers) {
        this.mapper = mapper;
        this.drivers = drivers;
    }

    @KafkaListener(topics = "${fdt.topics.orders-events}", groupId = "driver-service")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String eventType = header(record, "event-type");
            if (!"OrderCreated".equals(eventType)) {
                ack.acknowledge();
                return;
            }
            OrderCreated event = mapper.readValue(record.value(), OrderCreated.class);
            log.info("Received OrderCreated {}", event.orderId());
            drivers.tryAssignNearestDriver(event.orderId(), event.pickupLat(), event.pickupLon());
            ack.acknowledge();
        } catch (Exception ex) {
            // Log and rethrow so Spring's error handler can apply retry/DLT.
            log.error("Failed to process record offset={} partition={}: {}",
                    record.offset(), record.partition(), ex.toString());
            throw new RuntimeException(ex);
        }
    }

    private static String header(ConsumerRecord<?, ?> rec, String key) {
        var h = rec.headers().lastHeader(key);
        return h == null ? null : new String(h.value());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderCreated(
            UUID orderId,
            double pickupLat,
            double pickupLon) {}
}
