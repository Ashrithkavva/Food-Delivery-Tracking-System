package com.fdt.order.dto;

import com.fdt.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record TransitionRequest(@NotNull OrderStatus target) {}
