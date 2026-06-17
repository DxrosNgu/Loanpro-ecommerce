package com.loanpro.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanpro.ecommerce.domain.OrderStatus;
import com.loanpro.ecommerce.dto.request.OrderItemRequest;
import com.loanpro.ecommerce.dto.request.OrderRequest;
import com.loanpro.ecommerce.dto.response.OrderResponse;
import com.loanpro.ecommerce.exception.InsufficientStockException;
import com.loanpro.ecommerce.exception.OrderNotFoundException;
import com.loanpro.ecommerce.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@DisplayName("OrderController")
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean OrderService orderService;

    private OrderRequest validRequest() {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(1L); item.setQuantity(2);
        OrderRequest req = new OrderRequest();
        req.setItems(List.of(item));
        req.setCardNumber("4111111111111111");
        return req;
    }

    private OrderResponse paidOrder() {
        return OrderResponse.builder()
            .id(42L).status(OrderStatus.PAID)
            .totalAmount(BigDecimal.valueOf(179.98))
            .paymentRef("TXN-ABCD1234")
            .items(List.of()).build();
    }

    @Test @DisplayName("POST /api/orders returns 201 with paid order")
    void placeOrderReturns201() throws Exception {
        when(orderService.placeOrder(any())).thenReturn(paidOrder());

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(validRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.paymentRef").value("TXN-ABCD1234"));
    }

    @Test @DisplayName("POST /api/orders returns FAILED status when card declined")
    void placeOrderReturnsFailedStatus() throws Exception {
        OrderResponse failed = OrderResponse.builder()
            .id(43L).status(OrderStatus.FAILED)
            .totalAmount(BigDecimal.valueOf(179.98))
            .items(List.of()).build();
        when(orderService.placeOrder(any())).thenReturn(failed);

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(validRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test @DisplayName("POST /api/orders returns 400 when items list is empty")
    void placeOrderRejectsMissingItems() throws Exception {
        OrderRequest req = new OrderRequest();
        req.setItems(List.of());
        req.setCardNumber("4111111111111111");

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("POST /api/orders returns 400 when card number is missing")
    void placeOrderRejectsMissingCard() throws Exception {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(1L); item.setQuantity(1);
        OrderRequest req = new OrderRequest();
        req.setItems(List.of(item));
        req.setCardNumber("");

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("POST /api/orders returns 409 when stock insufficient")
    void placeOrderReturns409OnInsufficientStock() throws Exception {
        when(orderService.placeOrder(any()))
            .thenThrow(new InsufficientStockException("RS-001", 5, 2));

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(validRequest())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test @DisplayName("GET /api/orders/{id} returns order receipt")
    void getOrderReturnsReceipt() throws Exception {
        when(orderService.getById(eq(42L))).thenReturn(paidOrder());

        mockMvc.perform(get("/api/orders/42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.totalAmount").value(179.98));
    }

    @Test @DisplayName("GET /api/orders/{id} returns 404 for unknown order")
    void getOrderReturns404() throws Exception {
        when(orderService.getById(eq(999L))).thenThrow(new OrderNotFoundException(999L));

        mockMvc.perform(get("/api/orders/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }
}
