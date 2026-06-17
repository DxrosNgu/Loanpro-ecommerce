package com.loanpro.ecommerce.dto.response;

import com.loanpro.ecommerce.domain.Order;
import com.loanpro.ecommerce.domain.OrderItem;
import com.loanpro.ecommerce.domain.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data @Builder
public class OrderResponse {
    private Long id;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String paymentRef;
    private List<OrderItemResponse> items;
    private Instant createdAt;

    @Data @Builder
    public static class OrderItemResponse {
        private Long productId;
        private String productName;
        private String productSku;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }

    public static OrderResponse from(Order o) {
        List<OrderItemResponse> items = o.getItems().stream()
            .map(i -> OrderItemResponse.builder()
                .productId(i.getProduct().getId())
                .productName(i.getProduct().getName())
                .productSku(i.getProduct().getSku())
                .quantity(i.getQuantity())
                .unitPrice(i.getUnitPrice())
                .subtotal(i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .build())
            .toList();

        return OrderResponse.builder()
            .id(o.getId())
            .status(o.getStatus())
            .totalAmount(o.getTotalAmount())
            .paymentRef(o.getPaymentRef())
            .items(items)
            .createdAt(o.getCreatedAt())
            .build();
    }
}
