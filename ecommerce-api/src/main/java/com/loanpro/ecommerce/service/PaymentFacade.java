package com.loanpro.ecommerce.service;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PaymentFacade {

    public PaymentResult charge(String cardNumber, BigDecimal amount) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return PaymentResult.failed("Card number is required");
        }

        String digits = cardNumber.replaceAll("\\D", "");

        if (digits.length() != 16) {
            return PaymentResult.failed("Card number must be exactly 16 digits");
        }

        if (digits.endsWith("0000")) {
            return PaymentResult.failed("Card declined by issuer");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return PaymentResult.failed("Invalid charge amount");
        }

        String txnId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return PaymentResult.success(txnId);
    }

    @Data @Builder
    public static class PaymentResult {
        private boolean success;
        private String transactionId;
        private String failureReason;

        static PaymentResult success(String txnId) {
            return PaymentResult.builder().success(true).transactionId(txnId).build();
        }

        static PaymentResult failed(String reason) {
            return PaymentResult.builder().success(false).failureReason(reason).build();
        }
    }
}
