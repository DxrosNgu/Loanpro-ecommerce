package com.loanpro.ecommerce.dto.request;

import com.loanpro.ecommerce.domain.Category;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "SKU is required")
    @Size(max = 100)
    private String sku;

    private String description;

    private Category category;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be zero or positive")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal price;

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stock;

    @DecimalMin(value = "0.0", message = "Weight must be positive")
    private BigDecimal weightKg;
}
