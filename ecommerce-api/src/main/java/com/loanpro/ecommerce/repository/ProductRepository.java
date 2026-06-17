package com.loanpro.ecommerce.repository;

import com.loanpro.ecommerce.domain.Category;
import com.loanpro.ecommerce.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByDeletedFalse(Pageable pageable);

    Optional<Product> findByIdAndDeletedFalse(Long id);

    Optional<Product> findBySkuAndDeletedFalse(String sku);

    Optional<Product> findBySku(String sku);

    boolean existsBySkuAndDeletedFalse(String sku);

    @Query("""
        SELECT p FROM Product p
        WHERE p.deleted = false
          AND (:q IS NULL
               OR LOWER(p.name) LIKE :q
               OR LOWER(p.description) LIKE :q
               OR LOWER(p.sku) LIKE :q)
          AND (:category IS NULL OR p.category = :category)
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
        """)
    Page<Product> searchInternal(
        @Param("q") String likePattern,
        @Param("category") Category category,
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        Pageable pageable
    );

    default Page<Product> search(String q, Category category,
                                   BigDecimal minPrice, BigDecimal maxPrice,
                                   Pageable pageable) {
        String likePattern = (q == null) ? null : "%" + q.toLowerCase() + "%";
        return searchInternal(likePattern, category, minPrice, maxPrice, pageable);
    }
}
