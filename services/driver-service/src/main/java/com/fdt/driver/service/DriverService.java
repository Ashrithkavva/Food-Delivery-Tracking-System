package com.fdt.driver.service;

import com.fdt.driver.domain.Driver;
import com.fdt.driver.domain.DriverAssignment;
import com.fdt.driver.dto.DriverDtos.*;
import com.fdt.driver.repository.DriverAssignmentRepository;
import com.fdt.driver.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    private final DriverRepository drivers;
    private final DriverAssignmentRepository assignments;
    private final TrackingClient tracking;

    public DriverService(DriverRepository drivers,
                         DriverAssignmentRepository assignments,
                         TrackingClient tracking) {
        this.drivers = drivers;
        this.assignments = assignments;
        this.tracking = tracking;
    }

    @Transactional
    public DriverResponse onboard(OnboardRequest req) {
        Driver d = Driver.onboard(req.fullName(), req.phone(), req.vehicleType(), req.licensePlate());
        drivers.save(d);
        log.info("Onboarded driver {} ({})", d.getId(), d.getFullName());
        return DriverResponse.from(d);
    }

    @Transactional(readOnly = true)
    public DriverResponse get(UUID id) {
        Driver d = drivers.findById(id).orElseThrow(() -> new IllegalArgumentException("driver not found: " + id));
        return DriverResponse.from(d);
    }

    @Transactional
    public DriverResponse updateStatus(UUID id, String status) {
        Driver d = drivers.findById(id).orElseThrow(() -> new IllegalArgumentException("driver not found: " + id));
        d.setStatus(Driver.Status.valueOf(status.toUpperCase()));
        return DriverResponse.from(d);
    }

    /**
     * Attempt to assign the nearest available driver to an order. Queries the
     * tracking-service geo-index, then walks the returned candidates and picks
     * the first one that is still AVAILABLE in our roster (the geo-index can
     * be slightly stale).
     */
    @Transactional
    public Optional<UUID> tryAssignNearestDriver(UUID orderId, double pickupLat, double pickupLon) {
        if (assignments.findByOrderId(orderId).isPresent()) {
            log.debug("Order {} already assigned, skipping", orderId);
            return Optional.empty();
        }

        List<TrackingClient.NearbyDriver> candidates =
                tracking.nearestDrivers(pickupLat, pickupLon, 5000, 20);

        for (TrackingClient.NearbyDriver candidate : candidates) {
            UUID driverId;
            try {
                driverId = UUID.fromString(candidate.driverId());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Optional<Driver> maybe = drivers.findById(driverId);
            if (maybe.isEmpty()) continue;
            Driver d = maybe.get();
            if (d.getStatus() != Driver.Status.AVAILABLE) continue;

            d.setStatus(Driver.Status.ON_DELIVERY);
            assignments.save(DriverAssignment.create(d.getId(), orderId));
            log.info("Assigned driver {} to order {} ({}m away)",
                    d.getId(), orderId, candidate.distanceMeters());
            return Optional.of(d.getId());
        }
        log.warn("No available drivers within range for order {}", orderId);
        return Optional.empty();
    }
}
