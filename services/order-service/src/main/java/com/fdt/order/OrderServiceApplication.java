package com.fdt.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Order service entry point.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Expose a REST API for creating, retrieving, and transitioning orders.</li>
 *   <li>Persist orders durably in PostgreSQL.</li>
 *   <li>Cache frequently-read active orders in Redis.</li>
 *   <li>Publish state-change events to Kafka via the transactional outbox.</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
