package com.fdt.order.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle states an order moves through.
 *
 * <p>Transitions are explicit; any attempt to move to a state that isn't in the
 * source state's {@link #allowedNext()} set is rejected at the service layer.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    READY_FOR_PICKUP,
    EN_ROUTE,
    DELIVERED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PENDING,            EnumSet.of(CONFIRMED, CANCELLED),
            CONFIRMED,          EnumSet.of(PREPARING, CANCELLED),
            PREPARING,          EnumSet.of(READY_FOR_PICKUP, CANCELLED),
            READY_FOR_PICKUP,   EnumSet.of(EN_ROUTE, CANCELLED),
            EN_ROUTE,           EnumSet.of(DELIVERED),
            DELIVERED,          EnumSet.noneOf(OrderStatus.class),
            CANCELLED,          EnumSet.noneOf(OrderStatus.class)
    );

    public Set<OrderStatus> allowedNext() {
        return ALLOWED.get(this);
    }

    public boolean canTransitionTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
