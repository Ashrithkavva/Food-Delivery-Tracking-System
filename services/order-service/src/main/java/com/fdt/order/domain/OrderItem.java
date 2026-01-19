package com.fdt.order.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_cents", nullable = false)
    private long unitCents;

    @Column(name = "notes")
    private String notes;

    protected OrderItem() {}

    public OrderItem(String sku, String name, int quantity, long unitCents, String notes) {
        this.sku = sku;
        this.name = name;
        this.quantity = quantity;
        this.unitCents = unitCents;
        this.notes = notes;
    }

    void setOrder(Order order) { this.order = order; }

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public long getUnitCents() { return unitCents; }
    public String getNotes() { return notes; }
}
