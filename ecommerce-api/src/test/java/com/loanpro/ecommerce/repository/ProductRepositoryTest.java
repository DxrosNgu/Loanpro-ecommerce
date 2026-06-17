package com.loanpro.ecommerce.repository;

import com.loanpro.ecommerce.domain.Category;
import com.loanpro.ecommerce.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for {@link ProductRepository#search}.
 *
 * <p><b>Context — production incident:</b> calling {@code GET /api/search?minPrice=30}
 * (i.e. {@code q == null}) against PostgreSQL threw:
 * <pre>ERROR: function lower(bytea) does not exist</pre>
 * This happened because the original JPQL wrapped the {@code :q} parameter directly in
 * {@code LOWER(CONCAT('%', :q, '%'))}. When {@code :q} is bound as a Java {@code null} with
 * no surrounding string literal for Hibernate to infer a type from, PostgreSQL's JDBC driver
 * falls back to {@code bytea} for the parameter's wire type, and {@code LOWER(bytea)} does
 * not exist as a function overload.
 *
 * <p><b>Why this test uses H2, not Postgres:</b> H2's JDBC driver does not exhibit the same
 * untyped-null inference behaviour, so this suite cannot reproduce the {@code bytea} error
 * itself — that failure is specific to PostgreSQL's parameter binding. What this suite DOES
 * guarantee is that the search query's filter logic (the part that's portable across
 * databases) stays correct after the fix. The actual fix was to stop passing {@code null}
 * through {@code LOWER()}/{@code CONCAT()} in JPQL entirely: the {@code %pattern%} string is
 * now built in Java (see {@link ProductRepository#search}), so the parameter sent to the
 * driver is always either a concrete {@code String} or a {@code null} typed by the method's
 * own parameter metadata — never a null routed through a SQL string function.
 *
 * <p>If this project adds Testcontainers with a real Postgres image, the single most
 * valuable test to add there is exactly the case below: {@code search(null, null, minPrice,
 * maxPrice, ...)} — every other null-handling case is already exercised here.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("ProductRepository.search()")
class ProductRepositoryTest {

    @org.springframework.beans.factory.annotation.Autowired
    private ProductRepository repo;

    @BeforeEach
    void seed() {
        repo.save(Product.builder()
            .name("Running Shoes").sku("RS-001")
            .description("Lightweight running shoes for daily training")
            .category(Category.FOOTWEAR)
            .price(BigDecimal.valueOf(89.99)).stock(150)
            .deleted(false).build());

        repo.save(Product.builder()
            .name("Wireless Mouse").sku("WM-042")
            .description("Ergonomic wireless mouse with USB receiver")
            .category(Category.ELECTRONICS)
            .price(BigDecimal.valueOf(29.99)).stock(75)
            .deleted(false).build());

        repo.save(Product.builder()
            .name("Standing Desk").sku("SD-004")
            .description("Adjustable height standing desk")
            .category(Category.HOME_AND_OFFICE)
            .price(BigDecimal.valueOf(449.99)).stock(15)
            .deleted(false).build());

        repo.save(Product.builder()
            .name("Deleted Product").sku("DEL-001")
            .description("Should never appear in search results")
            .category(Category.MISC)
            .price(BigDecimal.valueOf(9.99)).stock(0)
            .deleted(true).build());
    }

    // ── The exact production bug: null keyword + only a price filter ──────────

    @Nested
    @DisplayName("null keyword (the bug's exact reproduction case)")
    class NullKeyword {

        @Test
        @DisplayName("minPrice only, q is null — must not throw and must filter correctly")
        void minPriceOnlyWithNullKeyword() {
            // This is the literal request that failed in production:
            // GET /api/search?minPrice=30&page=0&size=12
            Page<Product> result = repo.search(
                null, null, BigDecimal.valueOf(30), null, PageRequest.of(0, 12));

            assertThat(result.getContent())
                .extracting(Product::getSku)
                .containsExactlyInAnyOrder("RS-001", "SD-004");
        }

        @Test
        @DisplayName("maxPrice only, q is null")
        void maxPriceOnlyWithNullKeyword() {
            Page<Product> result = repo.search(
                null, null, null, BigDecimal.valueOf(50), PageRequest.of(0, 12));

            assertThat(result.getContent())
                .extracting(Product::getSku)
                .containsExactly("WM-042");
        }

        @Test
        @DisplayName("category only, q is null")
        void categoryOnlyWithNullKeyword() {
            Page<Product> result = repo.search(
                null, Category.ELECTRONICS, null, null, PageRequest.of(0, 12));

            assertThat(result.getContent())
                .extracting(Product::getSku)
                .containsExactly("WM-042");
        }

        @Test
        @DisplayName("all filters null — returns every non-deleted product")
        void allFiltersNull() {
            Page<Product> result = repo.search(
                null, null, null, null, PageRequest.of(0, 12));

            assertThat(result.getContent()).hasSize(3); // excludes the deleted one
        }
    }

    // ── Keyword search across name / description / sku ────────────────────────

    @Nested
    @DisplayName("keyword search")
    class KeywordSearch {

        @Test
        @DisplayName("matches by product name, case-insensitively")
        void matchesByNameCaseInsensitive() {
            Page<Product> result = repo.search(
                "RUNNING", null, null, null, PageRequest.of(0, 12));

            assertThat(result.getContent())
                .extracting(Product::getSku)
                .containsExactly("RS-001");
        }

        @Test
        @DisplayName("matches by description")
        void matchesByDescription() {
            Page<Product> result = repo.search(
                "ergonomic", null, null, null, PageRequest.of(0, 12));

            assertThat(result.getContent())
                .extracting(Product::getSku)
                .containsExactly("WM-042");
        }

        @Test
        @DisplayName("matches by SKU substring")
        void matchesBySkuSubstring() {
            Page<Product> result = repo.search(
                "sd-0", null, null, null, PageRequest.of(0, 12));

            assertThat(result.getContent())
                .extracting(Product::getSku)
                .containsExactly("SD-004");
        }

        @Test
        @DisplayName("no match returns an empty page, not an error")
        void noMatchReturnsEmptyPage() {
            Page<Product> result = repo.search(
                "nonexistent-keyword-xyz", null, null, null, PageRequest.of(0, 12));

            assertThat(result.getContent()).isEmpty();
        }
    }

    // ── Combined filters ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("combined filters")
    class CombinedFilters {

        @Test
        @DisplayName("keyword + category + price range together")
        void keywordCategoryAndPriceRange() {
            Page<Product> result = repo.search(
                "wireless", Category.ELECTRONICS,
                BigDecimal.valueOf(10), BigDecimal.valueOf(50),
                PageRequest.of(0, 12));

            assertThat(result.getContent())
                .extracting(Product::getSku)
                .containsExactly("WM-042");
        }

        @Test
        @DisplayName("keyword matches but category excludes — returns empty")
        void keywordMatchesButCategoryExcludes() {
            Page<Product> result = repo.search(
                "wireless", Category.FOOTWEAR, null, null, PageRequest.of(0, 12));

            assertThat(result.getContent()).isEmpty();
        }
    }

    // ── Soft-delete exclusion ─────────────────────────────────────────────────

    @Test
    @DisplayName("never returns soft-deleted products regardless of filters")
    void excludesSoftDeletedProducts() {
        Page<Product> result = repo.search(
            "deleted", null, null, null, PageRequest.of(0, 12));

        assertThat(result.getContent()).isEmpty();
    }
}
