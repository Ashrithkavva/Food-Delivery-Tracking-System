package com.fdt.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fdt.order.cache.ActiveOrderCache;
import com.fdt.order.domain.*;
import com.fdt.order.dto.CreateOrderRequest;
import com.fdt.order.dto.OrderResponse;
import com.fdt.order.exception.OrderNotFoundException;
import com.fdt.order.kafka.OrderEvent;
import com.fdt.order.repository.OrderRepository;
import com.fdt.order.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String AGGREGATE = "Order";

    private final OrderRepository orders;
    private final OutboxEventRepository outbox;
    private final ActiveOrderCache cache;
    private final ObjectMapper mapper;

    public OrderService(OrderRepository orders,
                        OutboxEventRepository outbox,
                        ActiveOrderCache cache,
                        ObjectMapper mapper) {
        this.orders = orders;
        this.outbox = outbox;
        this.cache = cache;
        this.mapper = mapper;
    }

    /**
     * Create an order. If the (customer, idempotencyKey) tuple has already
     * been used, return the existing order rather than creating a duplicate.
     */
    @Transactional
    public OrderResponse create(CreateOrderRequest req) {
        return orders.findByCustomerIdAndIdempotencyKey(req.customerId(), req.idempotencyKey())
                .map(existing -> {
                    log.info("Idempotent replay for order {}", existing.getId());
                    return OrderResponse.from(existing);
                })
                .orElseGet(() -> doCreate(req));
    }

    private OrderResponse doCreate(CreateOrderRequest req) {
        long subtotal = req.items().stream()
                .mapToLong(i -> (long) i.quantity() * i.unitCents())
                .sum();

        Order order = Order.create(
                req.customerId(), req.restaurantId(), req.idempotencyKey(),
                req.pickupLat(), req.pickupLon(),
                req.dropoffLat(), req.dropoffLon(),
                subtotal, req.deliveryCents(),
                req.currency());

        for (CreateOrderRequest.Item item : req.items()) {
            order.addItem(new OrderItem(
                    item.sku(), item.name(), item.quantity(),
                    item.unitCents(), item.notes()));
        }

        Order saved = orders.save(order);

        publish(new OrderEvent.OrderCreated(
                saved.getId(), saved.getCustomerId(), saved.getRestaurantId(),
                saved.getPickupLat(), saved.getPickupLon(),
                saved.getDropoffLat(), saved.getDropoffLon(),
                saved.getTotalCents(), saved.getCurrency(),
                Instant.now()));

        OrderResponse response = OrderResponse.from(saved);
        cache.put(response);
        log.info("Created order {} for customer {}", saved.getId(), saved.getCustomerId());
        return response;
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return cache.get(id).orElseGet(() -> {
            Order o = orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
            OrderResponse r = OrderResponse.from(o);
            if (!o.getStatus().isTerminal()) cache.put(r);
            return r;
        });
    }

    @Transactional
    public OrderResponse transition(UUID id, OrderStatus target) {
        Order order = orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        OrderStatus from = order.getStatus();
        order.transitionTo(target);

        publish(new OrderEvent.OrderStatusChanged(order.getId(), from, target, Instant.now()));
        if (target == OrderStatus.CANCELLED) {
            publish(new OrderEvent.OrderCancelled(order.getId(), "user_or_system", Instant.now()));
        }

        OrderResponse response = OrderResponse.from(order);
        if (order.getStatus().isTerminal()) cache.invalidate(order.getId());
        else cache.put(response);
        return response;
    }

    @Transactional
    public OrderResponse assignDriver(UUID orderId, UUID driverId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        order.assignDriver(driverId);
        publish(new OrderEvent.DriverAssigned(orderId, driverId, Instant.now()));
        OrderResponse r = OrderResponse.from(order);
        cache.put(r);
        return r;
    }

    /** Append an event to the outbox in the current transaction. */
    private void publish(OrderEvent event) {
        try {
            String json = mapper.writeValueAsString(event);
            outbox.save(new OutboxEvent(AGGREGATE, event.orderId(), event.type(), json));
        } catch (JsonProcessingException e) {
            // If we can't serialize an event the only safe thing is to fail
            // the surrounding transaction so the domain change is rolled back.
            throw new IllegalStateException("Failed to serialize outbox event", e);
        }
    }
}
