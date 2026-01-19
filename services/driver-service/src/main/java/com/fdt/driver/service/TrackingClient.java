package com.fdt.driver.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Thin RestClient wrapper over tracking-service.
 *
 * Failure mode: if tracking is unreachable we return an empty list rather
 * than propagating. The Kafka listener will give up assigning this order
 * and rely on the standard retry/DLQ logic.
 */
@Component
public class TrackingClient {

    private static final Logger log = LoggerFactory.getLogger(TrackingClient.class);

    private final RestClient client;

    public TrackingClient(@Value("${fdt.tracking.base-url}") String baseUrl) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public List<NearbyDriver> nearestDrivers(double lat, double lon, int radiusMeters, int limit) {
        try {
            NearestResponse resp = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/drivers/nearest")
                            .queryParam("lat", lat)
                            .queryParam("lon", lon)
                            .queryParam("radius", radiusMeters)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.warn("tracking-service returned {}", res.getStatusCode());
                    })
                    .body(NearestResponse.class);
            if (resp == null || resp.drivers() == null) return Collections.emptyList();
            return resp.drivers();
        } catch (Exception e) {
            log.warn("tracking-service unreachable: {}", e.toString());
            return Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NearbyDriver(String driverId, double distanceMeters, double lat, double lon) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NearestResponse(List<NearbyDriver> drivers, int count) {}
}
