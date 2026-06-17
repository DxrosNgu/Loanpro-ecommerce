package com.loanpro.ecommerce.service;

import com.loanpro.ecommerce.dto.response.CsvImportResult;
import com.loanpro.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("CsvImportService (integration)")
class CsvImportServiceTest {

    @Autowired CsvImportService csvImportService;
    @Autowired ProductRepository productRepository;

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "test.csv", "text/csv",
            content.getBytes(StandardCharsets.UTF_8));
    }

    private static final String HEADER = "name,sku,description,category,price,stock,weight_kg\n";

    // ── Happy path ────────────────────────────────────────────────────────

    @Nested @DisplayName("valid import")
    class ValidImport {

        @Test @DisplayName("imports a single valid row")
        void importsSingleRow() throws Exception {
            CsvImportResult r = csvImportService.importFile(
                csv(HEADER + "Test Shoe,TST-001,A test shoe,Footwear,49.99,100,0.4\n"));
            assertThat(r.getImported()).isEqualTo(1);
            assertThat(r.getErrors()).isEmpty();
        }

        @Test @DisplayName("imports multiple valid rows in one file")
        void importsMultipleRows() throws Exception {
            CsvImportResult r = csvImportService.importFile(csv(HEADER
                + "Shoe A,SA-001,,Footwear,10.00,10,\n"
                + "Shoe B,SB-001,,Footwear,20.00,20,\n"
                + "Shoe C,SC-001,,Footwear,30.00,30,\n"));
            assertThat(r.getImported()).isEqualTo(3);
            assertThat(r.getErrors()).isEmpty();
        }

        @Test @DisplayName("skips fully blank trailing rows silently")
        void skipsBlankRows() throws Exception {
            CsvImportResult r = csvImportService.importFile(
                csv(HEADER + "Good Shoe,GS-001,,Footwear,9.99,5,\n\n\n"));
            assertThat(r.getImported()).isEqualTo(1);
            assertThat(r.getSkipped()).isGreaterThanOrEqualTo(2);
        }
    }

    // ── Price edge cases ──────────────────────────────────────────────────

    @Nested @DisplayName("price edge cases")
    class PriceEdgeCases {

        @Test @DisplayName("strips dollar prefix and imports row")
        void handlesDollarPrefix() throws Exception {
            CsvImportResult r = csvImportService.importFile(
                csv(HEADER + "Dollar Mouse,DLR-001,desc,Electronics,$29.99,50,0.3\n"));
            assertThat(r.getImported()).isEqualTo(1);
            assertThat(r.getErrors()).isEmpty();
            var saved = productRepository.findBySku("DLR-001");
            assertThat(saved).isPresent();
            assertThat(saved.get().getPrice()).isEqualByComparingTo("29.99");
        }

        @Test @DisplayName("maps 'free' price to 0.00 and imports row")
        void handlesFreePrice() throws Exception {
            CsvImportResult r = csvImportService.importFile(
                csv(HEADER + "Free Mat,FRE-001,desc,Sports,free,200,1.2\n"));
            assertThat(r.getImported()).isEqualTo(1);
            var saved = productRepository.findBySku("FRE-001");
            assertThat(saved.get().getPrice()).isEqualByComparingTo("0.00");
        }

        @Test @DisplayName("rejects row where price is a non-numeric string")
        void rejectsNonNumericPrice() throws Exception {
            CsvImportResult r = csvImportService.importFile(
                csv(HEADER + "Bad Price,BP-001,desc,Electronics,N/A,10,0.1\n"));
            assertThat(r.getImported()).isZero();
            assertThat(r.getErrors()).hasSize(1);
        }
    }

    // ── Stock edge cases ──────────────────────────────────────────────────

    @Nested @DisplayName("stock edge cases")
    class StockEdgeCases {

        @Test @DisplayName("rejects row with negative stock")
        void rejectsNegativeStock() throws Exception {
            CsvImportResult r = csvImportService.importFile(
                csv(HEADER + "Bad Product,BAD-001,desc,Electronics,10.00,-5,0.1\n"));
            assertThat(r.getImported()).isZero();
            assertThat(r.getErrors()).hasSize(1);
            assertThat(r.getErrors().get(0).getReason()).containsIgnoringCase("negative");
        }

        @Test @DisplayName("accepts row with zero stock (out-of-stock product)")
        void acceptsZeroStock() throws Exception {
            CsvImportResult r = csvImportService.importFile(
                csv(HEADER + "Out Clock,OC-001,desc,Home & Office,299.99,0,2.0\n"));
            assertThat(r.getImported()).isEqualTo(1);
        }
    }

    // ── Security edge cases ───────────────────────────────────────────────

    @Nested @DisplayName("security edge cases")
    class SecurityEdgeCases {

        @Test @DisplayName("sanitises XSS in name and imports cleaned row")
        void sanitisesXssInName() throws Exception {
            CsvImportResult r = csvImportService.importFile(
                csv(HEADER + "<script>alert('xss')</script>,XSS-001,desc,Electronics,19.99,100,0.1\n"));
            assertThat(r.getImported()).isEqualTo(1);
            var saved = productRepository.findBySku("XSS-001");
            assertThat(saved).isPresent();
            assertThat(saved.get().getName()).doesNotContain("<script>");
        }

        @Test @DisplayName("sanitises XSS in description and imports cleaned row")
        void sanitisesXssInDescription() throws Exception {
            CsvImportResult r = csvImportService.importFile(
                csv(HEADER + "Good Name,XSS-002,<img src=x onerror=alert(1)>,Electronics,19.99,100,0.1\n"));
            assertThat(r.getImported()).isEqualTo(1);
            var saved = productRepository.findBySku("XSS-002");
            assertThat(saved.get().getDescription()).doesNotContain("onerror");
        }

        @Test @DisplayName("SQL injection in name is handled by JPA (parameterised queries)")
        void handlesSqlInjection() throws Exception {
            CsvImportResult r = csvImportService.importFile(
                csv(HEADER + "'; DROP TABLE products;--,SQL-001,desc,Electronics,9.99,5,0.1\n"));
            assertThat(r.getImported()).isEqualTo(1);
            assertThat(productRepository.findBySku("SQL-001")).isPresent();
        }
    }

    // ── Name / SKU validation ─────────────────────────────────────────────

    @Nested @DisplayName("name and SKU validation")
    class NameSkuValidation {

        @Test @DisplayName("rejects row with blank name")
        void rejectsBlankName() throws Exception {
            CsvImportResult r = csvImportService.importFile(
                csv(HEADER + ",BLK-001,desc,Electronics,10.00,10,0.1\n"));
            assertThat(r.getErrors()).hasSize(1);
            assertThat(r.getErrors().get(0).getReason()).containsIgnoringCase("name");
        }

        @Test @DisplayName("rejects row with whitespace-only name")
        void rejectsWhitespaceName() throws Exception {
            CsvImportResult r = csvImportService.importFile(
                csv(HEADER + "   ,WS-001,desc,Electronics,10.00,10,0.1\n"));
            assertThat(r.getErrors()).hasSize(1);
        }
    }

    // ── Duplicate SKU (upsert) ────────────────────────────────────────────

    @Nested @DisplayName("duplicate SKU upsert")
    class DuplicateSku {

        @Test @DisplayName("updates existing product when SKU already exists")
        void upsertsExistingSku() throws Exception {
            csvImportService.importFile(csv(HEADER + "Original,DUP-001,first,Footwear,10.00,10,0.1\n"));
            CsvImportResult r = csvImportService.importFile(
                csv(HEADER + "Updated,DUP-001,second,Footwear,20.00,20,0.1\n"));

            assertThat(r.getUpdated()).isEqualTo(1);
            assertThat(r.getImported()).isZero();
            var saved = productRepository.findBySku("DUP-001");
            assertThat(saved.get().getName()).isEqualTo("Updated");
            assertThat(saved.get().getPrice()).isEqualByComparingTo("20.00");
        }

        @Test @DisplayName("handles mix of new and duplicate SKUs in same file")
        void handlesMixedSkus() throws Exception {
            csvImportService.importFile(csv(HEADER + "Original,MIX-001,first,Footwear,10.00,10,0.1\n"));
            CsvImportResult r = csvImportService.importFile(csv(HEADER
                + "Updated,MIX-001,second,Footwear,20.00,20,0.1\n"
                + "Brand New,MIX-002,new,Footwear,30.00,30,0.2\n"));

            assertThat(r.getUpdated()).isEqualTo(1);
            assertThat(r.getImported()).isEqualTo(1);
        }
    }

    // ── Mixed file (valid + invalid rows) ────────────────────────────────

    @Test @DisplayName("imports valid rows and rejects invalid rows in same file")
    void importsMixedFile() throws Exception {
        CsvImportResult r = csvImportService.importFile(csv(HEADER
            + "Good Shoe,MX-001,,Footwear,29.99,50,0.3\n"  // valid
            + ",MX-002,,Footwear,19.99,10,0.1\n"            // blank name → rejected
            + "Mouse,MX-003,,Electronics,$39.99,20,0.1\n"   // $ price → cleaned + imported
            + "Bad Stock,MX-004,,Electronics,9.99,-1,0.1\n" // negative stock → rejected
        ));

        assertThat(r.getImported()).isEqualTo(2);
        assertThat(r.getErrors()).hasSize(2);
    }
}
