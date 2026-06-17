package com.loanpro.ecommerce.service;

import com.loanpro.ecommerce.domain.Category;
import com.loanpro.ecommerce.domain.Product;
import com.loanpro.ecommerce.dto.request.ProductRequest;
import com.loanpro.ecommerce.dto.response.PageResponse;
import com.loanpro.ecommerce.dto.response.ProductResponse;
import com.loanpro.ecommerce.exception.ProductNotFoundException;
import com.loanpro.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repo;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(int page, int size, String sort) {
        var pageable = PageRequest.of(page, size, Sort.by(sort.equals("price") ? "price" : "name"));
        return PageResponse.from(repo.findByDeletedFalse(pageable).map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return ProductResponse.from(findActive(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest req) {
        Product p = Product.builder()
            .name(req.getName())
            .sku(req.getSku().toUpperCase())
            .description(req.getDescription())
            .category(req.getCategory())
            .price(req.getPrice())
            .stock(req.getStock())
            .weightKg(req.getWeightKg())
            .build();
        return ProductResponse.from(repo.save(p));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest req) {
        Product p = findActive(id);
        p.setName(req.getName());
        p.setSku(req.getSku().toUpperCase());
        p.setDescription(req.getDescription());
        p.setCategory(req.getCategory());
        p.setPrice(req.getPrice());
        p.setStock(req.getStock());
        p.setWeightKg(req.getWeightKg());
        return ProductResponse.from(repo.save(p));
    }

    @Transactional
    public void delete(Long id) {
        Product p = findActive(id);
        p.setDeleted(true);
        repo.save(p);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String q, String category,
                                                 BigDecimal minPrice, BigDecimal maxPrice,
                                                 int page, int size) {
        Category cat = (category != null && !category.isBlank())
            ? Category.valueOf(category.toUpperCase()) : null;
        var pageable = PageRequest.of(page, size, Sort.by("name"));
        String qParam = (q != null && !q.isBlank()) ? q.trim() : null;
        return PageResponse.from(
            repo.search(qParam, cat, minPrice, maxPrice, pageable).map(ProductResponse::from)
        );
    }

    public Product findActive(Long id) {
        return repo.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
