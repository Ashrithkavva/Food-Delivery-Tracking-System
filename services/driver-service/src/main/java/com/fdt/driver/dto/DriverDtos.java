package com.fdt.driver.dto;

import com.fdt.driver.domain.Driver;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class DriverDtos {

    public record OnboardRequest(
            @NotBlank @Size(max = 255) String fullName,
            @NotBlank @Size(max = 32) String phone,
            @NotBlank @Size(max = 32) String vehicleType,
            @Size(max = 32) String licensePlate
    ) {}

    public record StatusUpdate(@NotBlank String status) {}

    public record DriverResponse(
            UUID id,
            String fullName,
            String phone,
            String vehicleType,
            String licensePlate,
            String status,
            BigDecimal rating,
            Instant onboardedAt,
            Instant lastStatusAt
    ) {
        public static DriverResponse from(Driver d) {
            return new DriverResponse(
                    d.getId(), d.getFullName(), d.getPhone(),
                    d.getVehicleType(), d.getLicensePlate(),
                    d.getStatus().name(), d.getRating(),
                    d.getOnboardedAt(), d.getLastStatusAt()
            );
        }
    }
}
