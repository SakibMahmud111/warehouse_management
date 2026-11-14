package com.waremanager.model;

import jakarta.persistence.*;

/**
 * A company the warehouse buys from. F6 predicts each supplier's lead time per product.
 * F5 (supplier scoring) and F19 (order consolidation) can build on this table later.
 */
@Entity
@Table(name = "suppliers")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    protected Supplier() {
    }

    public Supplier(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
