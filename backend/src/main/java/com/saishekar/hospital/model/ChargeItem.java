package com.saishekar.hospital.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// One charged line on an invoice: what was billed, how many, and the
// unit price in cents. Amounts are integer cents, never floating point,
// so a total is exact. A line has no identity of its own -- it is
// written, read, and replaced as part of its invoice -- so it is an
// embeddable owned by the invoice rather than an entity.
@Embeddable
public class ChargeItem {

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_cents", nullable = false)
    private long unitPriceCents;

    protected ChargeItem() {
    }

    public ChargeItem(String description, int quantity, long unitPriceCents) {
        this.description = description;
        this.quantity = quantity;
        this.unitPriceCents = unitPriceCents;
    }

    public long getLineTotalCents() {
        return quantity * unitPriceCents;
    }

    public String getDescription() { return description; }
    public int getQuantity() { return quantity; }
    public long getUnitPriceCents() { return unitPriceCents; }
}
