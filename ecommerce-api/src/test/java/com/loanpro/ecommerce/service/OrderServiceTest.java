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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock OrderRepository orderRepo;
    @Mock ProductRepository productRepo;
    @Mock PaymentFacade payment;
    @InjectMocks OrderService service;

    private Product shoe;

    @BeforeEach
    void setUp() {
        shoe = Product.builder()
            .id(1L).name("Running Shoes").sku("RS-001")
            .price(BigDecimal.valueOf(89.99)).stock(10)
            .deleted(false).build();
    }

    private OrderRequest request(String card) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(1L);
        item.setQuantity(2);
        OrderRequest req = new OrderRequest();
        req.setItems(List.of(item));
        req.setCardNumber(card);
        return req;
    }

    private Order savedOrder(OrderStatus status, BigDecimal total) {
        return Order.builder()
            .id(42L).status(status).totalAmount(total)
            .paymentRef(status == OrderStatus.PAID ? "TXN-ABCD1234" : null)
            .items(List.of()).build();
    }

    @Nested @DisplayName("placeOrder()")
    class PlaceOrder {

        @Test @DisplayName("creates PAID order when payment succeeds")
        void createsPaidOrder() {
            when(productRepo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(shoe));
            when(payment.charge(eq("4111111111111111"), any()))
                .thenReturn(PaymentFacade.PaymentResult.success("TXN-ABCD1234"));
            when(orderRepo.save(any(Order.class)))
                .thenReturn(savedOrder(OrderStatus.PAID, BigDecimal.valueOf(179.98)));

            OrderResponse resp = service.placeOrder(request("4111111111111111"));

            assertThat(resp.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(resp.getPaymentRef()).isEqualTo("TXN-ABCD1234");
        }

        @Test @DisplayName("creates FAILED order when card is declined")
        void createsFailedOrder() {
            when(productRepo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(shoe));
            when(payment.charge(eq("4111111110000"), any()))
                .thenReturn(PaymentFacade.PaymentResult.failed("Card declined"));
            when(orderRepo.save(any(Order.class)))
                .thenReturn(savedOrder(OrderStatus.FAILED, BigDecimal.valueOf(179.98)));

            OrderResponse resp = service.placeOrder(request("4111111110000"));

            assertThat(resp.getStatus()).isEqualTo(OrderStatus.FAILED);
        }

        @Test @DisplayName("decrements stock only on successful payment")
        void decrementsStockOnSuccess() {
            when(productRepo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(shoe));
            when(payment.charge(any(), any()))
                .thenReturn(PaymentFacade.PaymentResult.success("TXN-X"));
            when(orderRepo.save(any())).thenReturn(savedOrder(OrderStatus.PAID, BigDecimal.TEN));

            service.placeOrder(request("4111111111111111"));

            assertThat(shoe.getStock()).isEqualTo(8);
        }

        @Test @DisplayName("does NOT decrement stock on failed payment")
        void doesNotDecrementStockOnFailure() {
            when(productRepo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(shoe));
            when(payment.charge(any(), any()))
                .thenReturn(PaymentFacade.PaymentResult.failed("Declined"));
            when(orderRepo.save(any())).thenReturn(savedOrder(OrderStatus.FAILED, BigDecimal.TEN));

            service.placeOrder(request("4111111110000"));

            assertThat(shoe.getStock()).isEqualTo(10);
        }

        @Test @DisplayName("throws InsufficientStockException when stock too low")
        void throwsWhenStockInsufficient() {
            shoe.setStock(1);
            when(productRepo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(shoe));

            OrderItemRequest item = new OrderItemRequest();
            item.setProductId(1L); item.setQuantity(5);
            OrderRequest req = new OrderRequest();
            req.setItems(List.of(item)); req.setCardNumber("4111111111111111");

            assertThatThrownBy(() -> service.placeOrder(req))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("RS-001");
        }

        @Test @DisplayName("throws ProductNotFoundException when product missing")
        void throwsWhenProductMissing() {
            when(productRepo.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

            OrderItemRequest item = new OrderItemRequest();
            item.setProductId(99L); item.setQuantity(1);
            OrderRequest req = new OrderRequest();
            req.setItems(List.of(item)); req.setCardNumber("4111111111111111");

            assertThatThrownBy(() -> service.placeOrder(req))
                .isInstanceOf(ProductNotFoundException.class);
        }

        @Test @DisplayName("calculates total correctly across multiple items")
        void calculatesTotal() {
            Product mouse = Product.builder()
                .id(2L).name("Wireless Mouse").sku("WM-042")
                .price(BigDecimal.valueOf(29.99)).stock(20).deleted(false).build();

            when(productRepo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(shoe));
            when(productRepo.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(mouse));
            when(payment.charge(any(), eq(BigDecimal.valueOf(89.99 + 29.99))))
                .thenReturn(PaymentFacade.PaymentResult.success("TXN-X"));

            Order persisted = Order.builder().id(1L).status(OrderStatus.PAID)
                .totalAmount(BigDecimal.valueOf(119.98)).items(List.of()).build();
            when(orderRepo.save(any())).thenReturn(persisted);

            OrderItemRequest i1 = new OrderItemRequest(); i1.setProductId(1L); i1.setQuantity(1);
            OrderItemRequest i2 = new OrderItemRequest(); i2.setProductId(2L); i2.setQuantity(1);
            OrderRequest req = new OrderRequest();
            req.setItems(List.of(i1, i2)); req.setCardNumber("4111111111111111");

            service.placeOrder(req);

            verify(payment).charge(any(), eq(BigDecimal.valueOf(119.98)));
        }
    }

    @Nested @DisplayName("getById()")
    class GetById {

        @Test @DisplayName("returns order when found")
        void returnsOrderWhenFound() {
            Order order = savedOrder(OrderStatus.PAID, BigDecimal.valueOf(89.99));
            when(orderRepo.findById(42L)).thenReturn(Optional.of(order));

            OrderResponse resp = service.getById(42L);

            assertThat(resp.getId()).isEqualTo(42L);
            assertThat(resp.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test @DisplayName("throws OrderNotFoundException for unknown id")
        void throwsForUnknownId() {
            when(orderRepo.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("999");
        }
    }
}
