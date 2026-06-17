package com.loanpro.ecommerce.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentFacade")
class PaymentFacadeTest {

    private final PaymentFacade facade = new PaymentFacade();

    @Nested @DisplayName("Successful charges")
    class Success {

        @Test @DisplayName("valid 16-digit Visa card succeeds")
        void visaSucceeds() {
            var r = facade.charge("4111111111111111", BigDecimal.valueOf(99.99));
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.getTransactionId()).startsWith("TXN-");
            assertThat(r.getTransactionId()).hasSize(12); // "TXN-" + 8 chars
        }

        @Test @DisplayName("transaction IDs are unique per call")
        void transactionIdsAreUnique() {
            var r1 = facade.charge("4111111111111111", BigDecimal.TEN);
            var r2 = facade.charge("4111111111111111", BigDecimal.TEN);
            assertThat(r1.getTransactionId()).isNotEqualTo(r2.getTransactionId());
        }

        @Test @DisplayName("Mastercard 13-digit minimum length succeeds")
        void minLengthSucceeds() {
            var r = facade.charge("4111111111111", BigDecimal.valueOf(10.00));
            assertThat(r.isSuccess()).isTrue();
        }

        @Test @DisplayName("19-digit card (maximum length) succeeds")
        void maxLengthSucceeds() {
            var r = facade.charge("4111111111111111111", BigDecimal.valueOf(50.00));
            assertThat(r.isSuccess()).isTrue();
        }

        @Test @DisplayName("spaces in card number are stripped before validation")
        void stripsSpaces() {
            var r = facade.charge("4111 1111 1111 1111", BigDecimal.TEN);
            assertThat(r.isSuccess()).isTrue();
        }

        @Test @DisplayName("price of 0.01 (minimum meaningful amount) succeeds")
        void minAmountSucceeds() {
            var r = facade.charge("4111111111111111", BigDecimal.valueOf(0.01));
            assertThat(r.isSuccess()).isTrue();
        }
    }

    @Nested @DisplayName("Declined cards")
    class Declined {

        @ParameterizedTest(name = "card {0} is declined")
        @ValueSource(strings = {"4111111111110000", "5500005555555550000", "1234560000"})
        @DisplayName("any card ending in 0000 is declined")
        void cardEndingIn0000IsDeclined(String card) {
            var r = facade.charge(card, BigDecimal.valueOf(50.00));
            assertThat(r.isSuccess()).isFalse();
            assertThat(r.getFailureReason()).containsIgnoringCase("declined");
        }

        @Test @DisplayName("failure result has no transaction ID")
        void failureHasNoTxnId() {
            var r = facade.charge("4111111111110000", BigDecimal.TEN);
            assertThat(r.getTransactionId()).isNull();
        }
    }

    @Nested @DisplayName("Invalid input")
    class InvalidInput {

        @Test @DisplayName("blank card number fails")
        void blankCardFails() {
            var r = facade.charge("", BigDecimal.TEN);
            assertThat(r.isSuccess()).isFalse();
        }

        @Test @DisplayName("null card number fails")
        void nullCardFails() {
            var r = facade.charge(null, BigDecimal.TEN);
            assertThat(r.isSuccess()).isFalse();
        }

        @Test @DisplayName("card with fewer than 13 digits fails")
        void tooShortCardFails() {
            var r = facade.charge("411111111111", BigDecimal.TEN); // 12 digits
            assertThat(r.isSuccess()).isFalse();
        }

        @Test @DisplayName("card with more than 19 digits fails")
        void tooLongCardFails() {
            var r = facade.charge("41111111111111111111", BigDecimal.TEN); // 20 digits
            assertThat(r.isSuccess()).isFalse();
        }

        @Test @DisplayName("zero amount fails")
        void zeroAmountFails() {
            var r = facade.charge("4111111111111111", BigDecimal.ZERO);
            assertThat(r.isSuccess()).isFalse();
        }

        @Test @DisplayName("negative amount fails")
        void negativeAmountFails() {
            var r = facade.charge("4111111111111111", BigDecimal.valueOf(-10.00));
            assertThat(r.isSuccess()).isFalse();
        }
    }
}
