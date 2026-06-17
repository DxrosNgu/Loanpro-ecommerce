package com.loanpro.ecommerce.controller;

import com.loanpro.ecommerce.dto.request.ProductRequest;
import com.loanpro.ecommerce.dto.response.PageResponse;
import com.loanpro.ecommerce.dto.response.ProductResponse;
import com.loanpro.ecommerce.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "CRUD for products")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "List all products (paginated)")
    public PageResponse<ProductResponse> list(
        @RequestParam(defaultValue = "0")  int page,
        @RequestParam(defaultValue = "12") int size,
        @RequestParam(defaultValue = "name") String sort) {
        return productService.list(page, size, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by ID")
    public ProductResponse get(@PathVariable Long id) {
        return productService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a product")
    public ProductResponse create(@Valid @RequestBody ProductRequest req) {
        return productService.create(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        return productService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a product")
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
