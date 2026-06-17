package com.loanpro.ecommerce.dto.response;

import com.loanpro.ecommerce.domain.Category;
import com.loanpro.ecommerce.domain.Product;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data @Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String sku;
    private String description;
    private Category category;
    private BigDecimal price;
    private Integer stock;
    private BigDecimal weightKg;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProductResponse from(Product p) {
        return ProductResponse.builder()
            .id(p.getId())
            .name(p.getName())
            .sku(p.getSku())
            .description(p.getDescription())
            .category(p.getCategory())
            .price(p.getPrice())
            .stock(p.getStock())
            .weightKg(p.getWeightKg())
            .createdAt(p.getCreatedAt())
            .updatedAt(p.getUpdatedAt())
            .build();
    }
}
