package com.fdt.driver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Driver service: manages the driver roster and on-shift state, listens for
 * OrderCreated events and triggers nearest-driver assignment.
 */
@SpringBootApplication
@EnableKafka
public class DriverServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DriverServiceApplication.class, args);
    }
}
