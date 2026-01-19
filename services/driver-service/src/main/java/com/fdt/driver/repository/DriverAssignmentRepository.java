package com.fdt.driver.repository;

import com.fdt.driver.domain.DriverAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverAssignmentRepository extends JpaRepository<DriverAssignment, UUID> {
    Optional<DriverAssignment> findByOrderId(UUID orderId);
}
