package com.waremanager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * A product stored in the warehouse (Model layer).
 *
 * The planning fields (cost, prices, lead time) are used by F1 (demand forecasting) and F2 (safety stock).
 * Each new column has a @ColumnDefault so Hibernate can add it to a products table
 * that already contains rows (PostgreSQL refuses a NOT NULL column without a default).
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must be at most 255 characters")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "SKU is required")
    @Size(max = 255, message = "SKU must be at most 255 characters")
    @Column(nullable = false, unique = true)
    private String sku;

    /** Units physically in the warehouse. */
    @NotNull
    @Min(value = 0, message = "Quantity must not be negative")
    @Column(nullable = false)
    private Integer quantity = 0;

    // ---------- Used by F1 and F2 (planning) ----------

    @NotBlank(message = "Category is required")
    @Size(max = 60, message = "Category must be at most 60 characters")
    @Column(nullable = false, length = 60)
    @ColumnDefault("'General'")
    private String category = "General";

    /** True for winter products (blankets, jackets...). Their demand follows the winter forecast. */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean seasonal;

    /** Purchase price of one unit (BDT). */
    @DecimalMin(value = "0.0", message = "Unit cost must not be negative")
    @Column(name = "unit_cost", nullable = false)
    @ColumnDefault("0")
    private double unitCost;

    /** Normal selling price of one unit (BDT). */
    @DecimalMin(value = "0.0", message = "Selling price must not be negative")
    @Column(name = "selling_price", nullable = false)
    @ColumnDefault("0")
    private double sellingPrice;

    /** What one unsold unit is worth after its season (clearance price, BDT). */
    @DecimalMin(value = "0.0", message = "Clearance price must not be negative")
    @Column(name = "clearance_price", nullable = false)
    @ColumnDefault("0")
    private double clearancePrice;

    /** Average days from placing an order to receiving it (used by F2). */
    @DecimalMin(value = "1.0", message = "Lead time must be at least 1 day")
    @Column(name = "lead_time_days", nullable = false)
    @ColumnDefault("7")
    private double leadTimeDays = 7;

    /** How much the lead time varies (standard deviation, days). */
    @DecimalMin(value = "0.0", message = "Lead time variation must not be negative")
    @Column(name = "lead_time_std_days", nullable = false)
    @ColumnDefault("2")
    private double leadTimeStdDays = 2;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isSeasonal() { return seasonal; }
    public void setSeasonal(boolean seasonal) { this.seasonal = seasonal; }

    public double getUnitCost() { return unitCost; }
    public void setUnitCost(double unitCost) { this.unitCost = unitCost; }

    public double getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(double sellingPrice) { this.sellingPrice = sellingPrice; }

    public double getClearancePrice() { return clearancePrice; }
    public void setClearancePrice(double clearancePrice) { this.clearancePrice = clearancePrice; }


    public double getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(double leadTimeDays) { this.leadTimeDays = leadTimeDays; }

    public double getLeadTimeStdDays() { return leadTimeStdDays; }
    public void setLeadTimeStdDays(double leadTimeStdDays) { this.leadTimeStdDays = leadTimeStdDays; }

}
