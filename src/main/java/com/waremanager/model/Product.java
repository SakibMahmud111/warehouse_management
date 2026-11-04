package com.waremanager.model;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
@Entity
@Table(name="products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank(message="Product name is required")
    @Column(nullable=false) private String name;
    @NotBlank(message="SKU is required")
    @Column(nullable=false, unique=true) private String sku;
    @NotNull @Min(value=0, message="Quantity must not be negative")
    @Column(nullable=false) private Integer quantity = 0;
    public Long getId(){return id;}
    public void setId(Long id){this.id=id;}
    public String getName(){return name;}
    public void setName(String name){this.name=name;}
    public String getSku(){return sku;}
    public void setSku(String sku){this.sku=sku;}
    public Integer getQuantity(){return quantity;}
    public void setQuantity(Integer quantity){this.quantity=quantity;}
}
