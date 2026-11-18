package com.waremanager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * A product stored in the warehouse (Model layer).
 *
 * The planning fields (cost, prices, lead time, minimum order...) are used by F1-F4.
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

    // ---------- Added for F1-F4 (planning) ----------

    @NotBlank(message = "Category is required")
    @Size(max = 60, message = "Category must be at most 60 characters")
    @Column(nullable = false, length = 60)
    @ColumnDefault("'General'")
    private String category = "General";

    /** Required substitute attributes, written as key=value pairs separated by commas. */
    @Size(max = 500)
    @Column(name = "key_attributes", length = 500)
    private String keyAttributes = "";

    /** Null for ambient products; both values are Celsius. */
    @Column(name = "min_temperature_c")
    private Double minTemperatureC;
    @Column(name = "max_temperature_c")
    private Double maxTemperatureC;

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

    /** Units ordered from the supplier but not received yet. Changed by the F3 suggestion buttons. */
    @Column(name = "quantity_on_order", nullable = false)
    @ColumnDefault("0")
    private int quantityOnOrder;

    /** Average days from placing an order to receiving it. F6 replaces it once 3 deliveries are recorded. */
    @DecimalMin(value = "1.0", message = "Lead time must be at least 1 day")
    @Column(name = "lead_time_days", nullable = false)
    @ColumnDefault("7")
    private double leadTimeDays = 7;

    /** How much the lead time varies (standard deviation, days). */
    @DecimalMin(value = "0.0", message = "Lead time variation must not be negative")
    @Column(name = "lead_time_std_days", nullable = false)
    @ColumnDefault("2")
    private double leadTimeStdDays = 2;

    /** The supplier will not accept smaller orders than this. */
    @Min(value = 1, message = "Minimum order quantity must be at least 1")
    @Column(name = "min_order_qty", nullable = false)
    @ColumnDefault("1")
    private int minOrderQty = 1;

    /** F6: who supplies this product (optional). */
    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** How many units the business usually orders today (compared with EOQ in F4). 0 = not set. */
    @Min(value = 0, message = "Current order quantity must not be negative")
    @Column(name = "current_order_qty", nullable = false)
    @ColumnDefault("0")
    private int currentOrderQty;

    /** Stock on hand plus stock already ordered: what we will have without ordering more. */
    public int getInventoryPosition() {
        return (quantity == null ? 0 : quantity) + quantityOnOrder;
    }

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

    public String getKeyAttributes() { return keyAttributes; }
    public void setKeyAttributes(String value) { keyAttributes = value; }
    public Double getMinTemperatureC() { return minTemperatureC; }
    public void setMinTemperatureC(Double value) { minTemperatureC = value; }
    public Double getMaxTemperatureC() { return maxTemperatureC; }
    public void setMaxTemperatureC(Double value) { maxTemperatureC = value; }

    public boolean isSeasonal() { return seasonal; }
    public void setSeasonal(boolean seasonal) { this.seasonal = seasonal; }

    public double getUnitCost() { return unitCost; }
    public void setUnitCost(double unitCost) { this.unitCost = unitCost; }

    public double getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(double sellingPrice) { this.sellingPrice = sellingPrice; }

    public double getClearancePrice() { return clearancePrice; }
    public void setClearancePrice(double clearancePrice) { this.clearancePrice = clearancePrice; }

    public int getQuantityOnOrder() { return quantityOnOrder; }
    public void setQuantityOnOrder(int quantityOnOrder) { this.quantityOnOrder = quantityOnOrder; }

    public double getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(double leadTimeDays) { this.leadTimeDays = leadTimeDays; }

    public double getLeadTimeStdDays() { return leadTimeStdDays; }
    public void setLeadTimeStdDays(double leadTimeStdDays) { this.leadTimeStdDays = leadTimeStdDays; }

    public int getMinOrderQty() { return minOrderQty; }
    public void setMinOrderQty(int minOrderQty) { this.minOrderQty = minOrderQty; }

    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }

    public int getCurrentOrderQty() { return currentOrderQty; }
    public void setCurrentOrderQty(int currentOrderQty) { this.currentOrderQty = currentOrderQty; }
}
