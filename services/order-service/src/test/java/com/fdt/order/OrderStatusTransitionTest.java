package com.fdt.order;

import com.fdt.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStatusTransitionTest {

    @Test
    void happyPathProgresses() {
        OrderStatus s = OrderStatus.PENDING;
        assertTrue(s.canTransitionTo(OrderStatus.CONFIRMED));
        s = OrderStatus.CONFIRMED;
        assertTrue(s.canTransitionTo(OrderStatus.PREPARING));
        s = OrderStatus.PREPARING;
        assertTrue(s.canTransitionTo(OrderStatus.READY_FOR_PICKUP));
        s = OrderStatus.READY_FOR_PICKUP;
        assertTrue(s.canTransitionTo(OrderStatus.EN_ROUTE));
        s = OrderStatus.EN_ROUTE;
        assertTrue(s.canTransitionTo(OrderStatus.DELIVERED));
    }

    @Test
    void cannotSkipStates() {
        assertFalse(OrderStatus.PENDING.canTransitionTo(OrderStatus.EN_ROUTE));
        assertFalse(OrderStatus.PREPARING.canTransitionTo(OrderStatus.DELIVERED));
    }

    @Test
    void terminalStatesCannotMove() {
        assertTrue(OrderStatus.DELIVERED.allowedNext().isEmpty());
        assertTrue(OrderStatus.CANCELLED.allowedNext().isEmpty());
    }

    @Test
    void deliveredAndCancelledAreTerminal() {
        assertTrue(OrderStatus.DELIVERED.isTerminal());
        assertTrue(OrderStatus.CANCELLED.isTerminal());
        assertFalse(OrderStatus.PENDING.isTerminal());
        assertFalse(OrderStatus.EN_ROUTE.isTerminal());
    }

    @Test
    void cancelAllowedFromMostStates() {
        assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED));
        assertTrue(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED));
        assertTrue(OrderStatus.PREPARING.canTransitionTo(OrderStatus.CANCELLED));
        // Once en route, no cancellations.
        assertFalse(OrderStatus.EN_ROUTE.canTransitionTo(OrderStatus.CANCELLED));
    }
}
