package com.loanpro.ecommerce.service;

import com.loanpro.ecommerce.domain.*;
import com.loanpro.ecommerce.dto.request.OrderItemRequest;
import com.loanpro.ecommerce.dto.request.OrderRequest;
import com.loanpro.ecommerce.dto.response.OrderResponse;
import com.loanpro.ecommerce.exception.InsufficientStockException;
import com.loanpro.ecommerce.exception.OrderNotFoundException;
import com.loanpro.ecommerce.exception.ProductNotFoundException;
import com.loanpro.ecommerce.repository.OrderRepository;
import com.loanpro.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final PaymentFacade payment;

    @Transactional
    public OrderResponse placeOrder(OrderRequest req) {
        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : req.getItems()) {
            Product product = productRepo.findByIdAndDeletedFalse(itemReq.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(itemReq.getProductId()));

            if (product.getStock() < itemReq.getQuantity()) {
                throw new InsufficientStockException(
                    product.getSku(), itemReq.getQuantity(), product.getStock());
            }

            BigDecimal unitPrice = product.getPrice();
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity())));

            items.add(OrderItem.builder()
                .product(product)
                .quantity(itemReq.getQuantity())
                .unitPrice(unitPrice)
                .build());
        }

        PaymentFacade.PaymentResult result = payment.charge(req.getCardNumber(), total);

        Order order = Order.builder()
            .status(result.isSuccess() ? OrderStatus.PAID : OrderStatus.FAILED)
            .totalAmount(total)
            .paymentRef(result.getTransactionId())
            .build();

        for (OrderItem item : items) {
            item.setOrder(order);
        }
        order.setItems(items);

        if (result.isSuccess()) {
            items.forEach(item ->
                item.getProduct().setStock(item.getProduct().getStock() - item.getQuantity()));
        }

        return OrderResponse.from(orderRepo.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        return OrderResponse.from(
            orderRepo.findById(id).orElseThrow(() -> new OrderNotFoundException(id)));
    }
}
