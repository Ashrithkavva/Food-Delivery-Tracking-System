package com.fdt.order.controller;

import com.fdt.order.dto.CreateOrderRequest;
import com.fdt.order.dto.OrderResponse;
import com.fdt.order.dto.TransitionRequest;
import com.fdt.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req) {
        OrderResponse created = service.create(req);
        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/transition")
    @ResponseStatus(HttpStatus.OK)
    public OrderResponse transition(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionRequest req) {
        return service.transition(id, req.target());
    }

    @PostMapping("/{id}/driver/{driverId}")
    @ResponseStatus(HttpStatus.OK)
    public OrderResponse assignDriver(
            @PathVariable UUID id,
            @PathVariable UUID driverId) {
        return service.assignDriver(id, driverId);
    }
}
