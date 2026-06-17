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

        @Test @DisplayName("valid 16-digit card succeeds")
        void sixteenDigitCardSucceeds() {
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

        @Test @DisplayName("spaces in card number are stripped before validation")
        void stripsSpaces() {
            var r = facade.charge("4111 1111 1111 1111", BigDecimal.TEN); // 16 digits once stripped
            assertThat(r.isSuccess()).isTrue();
        }

        @Test @DisplayName("dashes in card number are stripped before validation")
        void stripsDashes() {
            var r = facade.charge("4111-1111-1111-1111", BigDecimal.TEN); // 16 digits once stripped
            assertThat(r.isSuccess()).isTrue();
        }

        @Test @DisplayName("price of 0.01 (minimum meaningful amount) succeeds")
        void minAmountSucceeds() {
            var r = facade.charge("4111111111111111", BigDecimal.valueOf(0.01));
            assertThat(r.isSuccess()).isTrue();
        }
    }

    @Nested @DisplayName("Declined cards (correct length, ends in 0000)")
    class Declined {

        @ParameterizedTest(name = "16-digit card {0} is declined")
        @ValueSource(strings = {"4111111111110000", "5500005555550000", "1234567890120000"})
        @DisplayName("any 16-digit card ending in 0000 is declined")
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

    @Nested @DisplayName("Invalid card length — must be exactly 16 digits")
    class InvalidLength {

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

        @Test @DisplayName("13-digit card fails (too short — old minimum no longer accepted)")
        void thirteenDigitCardFails() {
            var r = facade.charge("4111111111111", BigDecimal.TEN); // 13 digits
            assertThat(r.isSuccess()).isFalse();
            assertThat(r.getFailureReason()).containsIgnoringCase("16 digits");
        }

        @Test @DisplayName("15-digit card fails (one digit short)")
        void fifteenDigitCardFails() {
            var r = facade.charge("411111111111111", BigDecimal.TEN); // 15 digits
            assertThat(r.isSuccess()).isFalse();
        }

        @Test @DisplayName("17-digit card fails (one digit over)")
        void seventeenDigitCardFails() {
            var r = facade.charge("41111111111111111", BigDecimal.TEN); // 17 digits
            assertThat(r.isSuccess()).isFalse();
        }

        @Test @DisplayName("19-digit card fails (old maximum no longer accepted)")
        void nineteenDigitCardFails() {
            var r = facade.charge("4111111111111111111", BigDecimal.TEN); // 19 digits
            assertThat(r.isSuccess()).isFalse();
            assertThat(r.getFailureReason()).containsIgnoringCase("16 digits");
        }

        @Test @DisplayName("single digit fails")
        void singleDigitFails() {
            var r = facade.charge("4", BigDecimal.TEN);
            assertThat(r.isSuccess()).isFalse();
        }
    }

    @Nested @DisplayName("Invalid amount")
    class InvalidAmount {

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
