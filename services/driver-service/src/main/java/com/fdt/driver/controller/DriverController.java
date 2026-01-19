package com.fdt.driver.controller;

import com.fdt.driver.dto.DriverDtos.*;
import com.fdt.driver.service.DriverService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverService service;

    public DriverController(DriverService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DriverResponse> onboard(@Valid @RequestBody OnboardRequest req) {
        DriverResponse created = service.onboard(req);
        return ResponseEntity
                .created(URI.create("/api/v1/drivers/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    public DriverResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}/status")
    public DriverResponse setStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdate body) {
        return service.updateStatus(id, body.status());
    }
}
