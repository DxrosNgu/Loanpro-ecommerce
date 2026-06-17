package com.loanpro.ecommerce.service;

import com.loanpro.ecommerce.domain.Category;
import com.loanpro.ecommerce.domain.Product;
import com.loanpro.ecommerce.dto.request.ProductRequest;
import com.loanpro.ecommerce.dto.response.PageResponse;
import com.loanpro.ecommerce.dto.response.ProductResponse;
import com.loanpro.ecommerce.exception.ProductNotFoundException;
import com.loanpro.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService")
class ProductServiceTest {

    @Mock ProductRepository repo;
    @InjectMocks ProductService service;

    private Product shoe;

    @BeforeEach
    void setUp() {
        shoe = Product.builder()
            .id(1L).name("Running Shoes").sku("RS-001")
            .price(BigDecimal.valueOf(89.99)).stock(150)
            .category(Category.FOOTWEAR).deleted(false).build();
    }

    // ── list ──────────────────────────────────────────────────────────────

    @Nested @DisplayName("list()")
    class List_ {

        @Test @DisplayName("returns paginated response")
        void returnsPaginatedResponse() {
            when(repo.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(shoe)));

            PageResponse<ProductResponse> result = service.list(0, 12, "name");

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getSku()).isEqualTo("RS-001");
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test @DisplayName("returns empty page when no products")
        void returnsEmptyPage() {
            when(repo.findByDeletedFalse(any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of()));

            PageResponse<ProductResponse> result = service.list(0, 12, "name");

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ── getById ───────────────────────────────────────────────────────────

    @Nested @DisplayName("getById()")
    class GetById {

        @Test @DisplayName("returns product when found")
        void returnsProductWhenFound() {
            when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(shoe));

            ProductResponse result = service.getById(1L);

            assertThat(result.getName()).isEqualTo("Running Shoes");
            assertThat(result.getSku()).isEqualTo("RS-001");
            assertThat(result.getPrice()).isEqualByComparingTo("89.99");
        }

        @Test @DisplayName("throws ProductNotFoundException for unknown id")
        void throwsForUnknownId() {
            when(repo.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");
        }
    }

    // ── create ────────────────────────────────────────────────────────────

    @Nested @DisplayName("create()")
    class Create {

        @Test @DisplayName("persists product and returns response")
        void persistsAndReturns() {
            ProductRequest req = new ProductRequest();
            req.setName("Running Shoes"); req.setSku("rs-001");
            req.setPrice(BigDecimal.valueOf(89.99)); req.setStock(150);
            req.setCategory(Category.FOOTWEAR);

            when(repo.save(any(Product.class))).thenReturn(shoe);

            ProductResponse result = service.create(req);

            assertThat(result.getSku()).isEqualTo("RS-001");
            verify(repo).save(argThat(p -> p.getSku().equals("RS-001")));
        }

        @Test @DisplayName("uppercases the SKU before saving")
        void uppercasesSku() {
            ProductRequest req = new ProductRequest();
            req.setName("Shoes"); req.setSku("rs-001");
            req.setPrice(BigDecimal.TEN); req.setStock(5);

            when(repo.save(any())).thenReturn(shoe);
            service.create(req);

            verify(repo).save(argThat(p -> p.getSku().equals("RS-001")));
        }
    }

    // ── update ────────────────────────────────────────────────────────────

    @Nested @DisplayName("update()")
    class Update {

        @Test @DisplayName("updates all fields and saves")
        void updatesAllFields() {
            ProductRequest req = new ProductRequest();
            req.setName("Trail Shoes"); req.setSku("TR-001");
            req.setPrice(BigDecimal.valueOf(99.99)); req.setStock(80);
            req.setCategory(Category.SPORTS);

            when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(shoe));
            when(repo.save(shoe)).thenReturn(shoe);

            service.update(1L, req);

            assertThat(shoe.getName()).isEqualTo("Trail Shoes");
            assertThat(shoe.getSku()).isEqualTo("TR-001");
            assertThat(shoe.getPrice()).isEqualByComparingTo("99.99");
            verify(repo).save(shoe);
        }

        @Test @DisplayName("throws ProductNotFoundException when product missing")
        void throwsWhenMissing() {
            when(repo.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());
            ProductRequest req = new ProductRequest();
            req.setName("X"); req.setSku("X"); req.setPrice(BigDecimal.ONE); req.setStock(1);

            assertThatThrownBy(() -> service.update(99L, req))
                .isInstanceOf(ProductNotFoundException.class);
        }
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Nested @DisplayName("delete()")
    class Delete {

        @Test @DisplayName("sets deleted=true (soft delete)")
        void softDeletesProduct() {
            when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(shoe));
            when(repo.save(shoe)).thenReturn(shoe);

            service.delete(1L);

            assertThat(shoe.getDeleted()).isTrue();
            verify(repo).save(shoe);
        }

        @Test @DisplayName("throws ProductNotFoundException for unknown id")
        void throwsForUnknownId() {
            when(repo.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ProductNotFoundException.class);
            verify(repo, never()).save(any());
        }
    }

    // ── search ────────────────────────────────────────────────────────────

    @Nested @DisplayName("search()")
    class Search {

        @Test @DisplayName("passes keyword to repository search")
        void passesKeywordToRepo() {
            when(repo.search(eq("shoe"), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(shoe)));

            PageResponse<ProductResponse> result = service.search("shoe", null, null, null, 0, 12);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test @DisplayName("converts category string to enum")
        void convertsCategoryToEnum() {
            when(repo.search(isNull(), eq(Category.FOOTWEAR), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(shoe)));

            PageResponse<ProductResponse> result = service.search(null, "FOOTWEAR", null, null, 0, 12);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test @DisplayName("treats blank query as null")
        void treatsBlanqQueryAsNull() {
            when(repo.search(isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(java.util.List.of()));

            service.search("   ", null, null, null, 0, 12);

            verify(repo).search(isNull(), isNull(), isNull(), isNull(), any());
        }

        @Test @DisplayName("passes price range to repository")
        void passesPriceRange() {
            BigDecimal min = BigDecimal.TEN, max = BigDecimal.valueOf(100);
            when(repo.search(isNull(), isNull(), eq(min), eq(max), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(shoe)));

            service.search(null, null, min, max, 0, 12);

            verify(repo).search(isNull(), isNull(), eq(min), eq(max), any());
        }
    }
}
