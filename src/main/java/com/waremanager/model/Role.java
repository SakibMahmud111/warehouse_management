package com.waremanager.model;

/**
 * The user roles (actors) listed in the SRS. Every account has exactly one role.
 *
 * The database stores the constant's name as text (for example "INVENTORY_PLANNER")
 * because AppUser maps this field with @Enumerated(EnumType.STRING).
 * Spring Security turns the role into the authority "ROLE_" + name(), e.g. "ROLE_ADMIN".
 */
public enum Role {
    ADMIN("Admin"),
    WAREHOUSE_MANAGER("Warehouse Manager"),
    INVENTORY_PLANNER("Inventory Planner"),
    PURCHASING_OFFICER("Purchasing Officer"),
    SALES_COORDINATOR("Sales Coordinator"),
    WAREHOUSE_STAFF("Warehouse Staff");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    /** Human-friendly name shown in the views. */
    public String getLabel() {
        return label;
    }
}
