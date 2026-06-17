package com.loanpro.ecommerce.controller;

import com.loanpro.ecommerce.dto.request.OrderRequest;
import com.loanpro.ecommerce.dto.response.OrderResponse;
import com.loanpro.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Place and retrieve orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place an order (fake payment)")
    public OrderResponse place(@Valid @RequestBody OrderRequest req) {
        return orderService.placeOrder(req);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order receipt")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.getById(id);
    }
}
