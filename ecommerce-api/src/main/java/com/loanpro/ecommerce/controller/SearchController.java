package com.loanpro.ecommerce.controller;

import com.loanpro.ecommerce.dto.response.PageResponse;
import com.loanpro.ecommerce.dto.response.ProductResponse;
import com.loanpro.ecommerce.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Full-text + filter product search")
public class SearchController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Search products by keyword, category, and price range")
    public PageResponse<ProductResponse> search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @RequestParam(defaultValue = "0")  int page,
        @RequestParam(defaultValue = "12") int size) {
        return productService.search(q, category, minPrice, maxPrice, page, size);
    }
}
