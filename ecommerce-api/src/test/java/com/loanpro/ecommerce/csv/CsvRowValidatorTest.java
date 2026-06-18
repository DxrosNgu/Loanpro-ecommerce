package com.loanpro.ecommerce.csv;

import com.loanpro.ecommerce.csv.CsvRowValidator.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DisplayName("CsvRowValidator")
class CsvRowValidatorTest {

    private String[] row(String name, String sku, String desc,
                         String cat, String price, String stock, String weight) {
        return new String[]{name, sku, desc, cat, price, stock, weight};
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Nested @DisplayName("valid rows")
    class ValidRows {

        @Test @DisplayName("accepts a fully valid row")
        void acceptsValidRow() {
            Result r = CsvRowValidator.validate(
                row("Running Shoes", "RS-001", "A shoe", "Footwear", "89.99", "150", "0.35"), 2);
            assertThat(r.isAccepted()).isTrue();
            assertThat(r.getRow().getName()).isEqualTo("Running Shoes");
            assertThat(r.getRow().getPrice()).isEqualByComparingTo("89.99");
            assertThat(r.getRow().getStock()).isEqualTo(150);
            assertThat(r.getRow().getWeightKg()).isEqualByComparingTo("0.35");
        }

        @Test @DisplayName("accepts zero price (free product)")
        void acceptsZeroPrice() {
            Result r = CsvRowValidator.validate(
                row("Mat", "MT-001", "", "", "0.00", "10", ""), 2);
            assertThat(r.isAccepted()).isTrue();
            assertThat(r.getRow().getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test @DisplayName("accepts zero stock (out-of-stock)")
        void acceptsZeroStock() {
            Result r = CsvRowValidator.validate(
                row("Clock", "CK-001", "", "", "9.99", "0", ""), 2);
            assertThat(r.isAccepted()).isTrue();
            assertThat(r.getRow().getStock()).isZero();
        }

        @Test @DisplayName("accepts missing weight (nullable)")
        void acceptsMissingWeight() {
            Result r = CsvRowValidator.validate(
                row("Shoe", "SH-001", "", "", "9.99", "5", ""), 2);
            assertThat(r.isAccepted()).isTrue();
            assertThat(r.getRow().getWeightKg()).isNull();
        }

        @Test @DisplayName("accepts missing category")
        void acceptsMissingCategory() {
            Result r = CsvRowValidator.validate(
                row("Shoe", "SH-002", "", "", "9.99", "5", "1.0"), 2);
            assertThat(r.isAccepted()).isTrue();
        }
    }

    // ── Price edge cases ──────────────────────────────────────────────────

    @Nested @DisplayName("price edge cases")
    class PriceEdgeCases {

        @Test @DisplayName("strips dollar sign from price")
        void stripsDollarSign() {
            Result r = CsvRowValidator.validate(
                row("Mouse", "WM-042", "", "", "$29.99", "5", ""), 2);
            assertThat(r.isAccepted()).isTrue();
            assertThat(r.getRow().getPrice()).isEqualByComparingTo("29.99");
        }

        @Test @DisplayName("maps 'free' to 0.00")
        void mapsFreeToZero() {
            Result r = CsvRowValidator.validate(
                row("Shoe", "FR-001", "", "", "free", "5", ""), 2);
            assertThat(r.isAccepted()).isTrue();
            assertThat(r.getRow().getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test @DisplayName("maps 'FREE' (uppercase) to 0.00")
        void mapsFreeUppercase() {
            Result r = CsvRowValidator.validate(
                row("Shoe", "FR-002", "", "", "FREE", "5", ""), 2);
            assertThat(r.isAccepted()).isTrue();
            assertThat(r.getRow().getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @ParameterizedTest(name = "price ''{0}'' is invalid")
        @ValueSource(strings = {"abc", "N/A", "tbd", "--"})
        @DisplayName("rejects non-numeric price strings")
        void rejectsNonNumericPrice(String badPrice) {
            Result r = CsvRowValidator.validate(
                row("Shoe", "SH-003", "", "", badPrice, "5", ""), 2);
            assertThat(r.isAccepted()).isFalse();
            assertThat(r.getError().getReason()).containsIgnoringCase("price");
        }

        @Test @DisplayName("rejects negative price")
        void rejectsNegativePrice() {
            Result r = CsvRowValidator.validate(
                row("Shoe", "SH-004", "", "", "-5.00", "5", ""), 2);
            assertThat(r.isAccepted()).isFalse();
        }

        @Test @DisplayName("rejects empty price field")
        void rejectsEmptyPrice() {
            Result r = CsvRowValidator.validate(
                row("Shoe", "SH-005", "", "", "", "5", ""), 2);
            assertThat(r.isAccepted()).isFalse();
        }
    }

    // ── Stock edge cases ──────────────────────────────────────────────────

    @Nested @DisplayName("stock edge cases")
    class StockEdgeCases {

        @Test @DisplayName("rejects negative stock")
        void rejectsNegativeStock() {
            Result r = CsvRowValidator.validate(
                row("Shoe", "DL-007", "", "", "9.99", "-5", ""), 2);
            assertThat(r.isAccepted()).isFalse();
            assertThat(r.getError().getReason()).containsIgnoringCase("negative");
            assertThat(r.getError().getSku()).isEqualTo("DL-007");
        }

        @ParameterizedTest(name = "stock ''{0}'' is invalid")
        @ValueSource(strings = {"abc", "N/A", "many", "1.5"})
        @DisplayName("rejects non-integer stock")
        void rejectsNonIntegerStock(String badStock) {
            Result r = CsvRowValidator.validate(
                row("Shoe", "SH-006", "", "", "9.99", badStock, ""), 2);
            assertThat(r.isAccepted()).isFalse();
        }

        @Test @DisplayName("error includes the row number for traceability")
        void errorIncludesRowNumber() {
            Result r = CsvRowValidator.validate(
                row("Shoe", "SH-007", "", "", "9.99", "-1", ""), 7);
            assertThat(r.getError().getRow()).isEqualTo(7);
        }
    }

    // ── Name / SKU validation ─────────────────────────────────────────────

    @Nested @DisplayName("name and SKU validation")
    class NameSkuValidation {

        @Test @DisplayName("rejects blank name")
        void rejectsBlankName() {
            Result r = CsvRowValidator.validate(
                row("", "HD-099", "", "", "9.99", "5", ""), 2);
            assertThat(r.isAccepted()).isFalse();
            assertThat(r.getError().getReason()).containsIgnoringCase("name");
        }

        @Test @DisplayName("rejects whitespace-only name")
        void rejectsWhitespaceName() {
            Result r = CsvRowValidator.validate(
                row("   ", "WS-001", "", "", "9.99", "5", ""), 2);
            assertThat(r.isAccepted()).isFalse();
            assertThat(r.getError().getReason()).containsIgnoringCase("name");
        }

        @Test @DisplayName("rejects blank SKU")
        void rejectsBlankSku() {
            Result r = CsvRowValidator.validate(
                row("Shoe", "", "", "", "9.99", "5", ""), 2);
            assertThat(r.isAccepted()).isFalse();
            assertThat(r.getError().getReason()).containsIgnoringCase("sku");
        }

        @Test @DisplayName("trims whitespace from name")
        void trimsName() {
            Result r = CsvRowValidator.validate(
                row("  Running Shoes  ", "RS-001", "", "", "9.99", "5", ""), 2);
            assertThat(r.isAccepted()).isTrue();
            assertThat(r.getRow().getName()).isEqualTo("Running Shoes");
        }
    }

    // ── Structural edge cases ─────────────────────────────────────────────

    @Nested @DisplayName("structural edge cases")
    class Structural {

        @Test @DisplayName("rejects row with too few columns")
        void rejectsTooFewColumns() {
            Result r = CsvRowValidator.validate(new String[]{"OnlyName"}, 2);
            assertThat(r.isAccepted()).isFalse();
        }

        @Test @DisplayName("passes XSS content through (sanitiser handles it in the service)")
        void passesXssThroughForSanitiser() {
            Result r = CsvRowValidator.validate(
                row("<script>alert('xss')</script>", "XS-001", "", "", "9.99", "5", ""), 2);
            assertThat(r.isAccepted()).isTrue();
        }

        @Test @DisplayName("passes SQL injection string through (JPA handles parameterised queries)")
        void passesSqlInjectionThrough() {
            Result r = CsvRowValidator.validate(
                row("'; DROP TABLE products;--", "SQL-001", "", "", "9.99", "5", ""), 2);
            assertThat(r.isAccepted()).isTrue();
        }
    }
}
