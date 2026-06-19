package com.loanpro.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanpro.ecommerce.domain.Category;
import com.loanpro.ecommerce.dto.request.ProductRequest;
import com.loanpro.ecommerce.dto.response.PageResponse;
import com.loanpro.ecommerce.dto.response.ProductResponse;
import com.loanpro.ecommerce.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper mapper;
    @MockBean
    ProductService productService;

    private ProductResponse sampleProduct() {
        return ProductResponse.builder()
                .id(1L).name("Running Shoes").sku("RS-001")
                .price(BigDecimal.valueOf(89.99)).stock(150)
                .category(Category.FOOTWEAR).build();
    }

    @Test
    void listReturnsPage() throws Exception {
        var page = PageResponse.<ProductResponse>builder()
                .content(List.of(sampleProduct())).page(0).size(12)
                .totalElements(1).totalPages(1).last(true).build();
        when(productService.list(0, 12, "name")).thenReturn(page);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("RS-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getByIdReturnsProduct() throws Exception {
        when(productService.getById(1L)).thenReturn(sampleProduct());
        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Running Shoes"));
    }

    @Test
    void createReturns201() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setName("Running Shoes"); req.setSku("RS-001");
        req.setPrice(BigDecimal.valueOf(89.99)); req.setStock(150);
        when(productService.create(any())).thenReturn(sampleProduct());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createReturnsBadRequestWhenNameMissing() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setSku("RS-001"); req.setPrice(BigDecimal.TEN); req.setStock(10);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"));
    }

    @Test
    void createReturnsBadRequestWhenSkuMissing() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setName("Running Shoes"); req.setPrice(BigDecimal.TEN); req.setStock(10);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sku").value("SKU is required"));
    }

    @Test
    void createReturnsBadRequestWhenPriceIsNull() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setName("Running Shoes"); req.setSku("RS-001"); req.setStock(10);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").value("Price is required"));
    }

    @Test
    void createReturnsBadRequestWhenPriceIsNegative() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setName("Running Shoes"); req.setSku("RS-001");
        req.setPrice(BigDecimal.valueOf(-1)); req.setStock(10);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").value("Price must be zero or positive"));
    }

    @Test
    void createReturnsBadRequestWhenStockIsNull() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setName("Running Shoes"); req.setSku("RS-001"); req.setPrice(BigDecimal.TEN);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.stock").value("Stock is required"));
    }

    @Test
    void createReturnsBadRequestWhenStockIsNegative() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setName("Running Shoes"); req.setSku("RS-001");
        req.setPrice(BigDecimal.TEN); req.setStock(-1);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.stock").value("Stock cannot be negative"));
    }

    // ── PUT validation ────────────────────────────────────────────────────────

    @Test
    void updateReturns200() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setName("Running Shoes Pro"); req.setSku("RS-001");
        req.setPrice(BigDecimal.valueOf(99.99)); req.setStock(100);
        when(productService.update(eq(1L), any())).thenReturn(sampleProduct());

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void updateReturnsBadRequestWhenNameBlank() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setName("   "); req.setSku("RS-001");
        req.setPrice(BigDecimal.TEN); req.setStock(10);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"));
    }

    @Test
    void updateReturnsBadRequestWhenSkuBlank() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setName("Running Shoes"); req.setSku("");
        req.setPrice(BigDecimal.TEN); req.setStock(10);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sku").value("SKU is required"));
    }

    @Test
    void updateReturnsBadRequestWhenPriceIsNull() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setName("Running Shoes"); req.setSku("RS-001"); req.setStock(10);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").value("Price is required"));
    }

    @Test
    void updateReturnsBadRequestWhenStockIsNull() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setName("Running Shoes"); req.setSku("RS-001"); req.setPrice(BigDecimal.TEN);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.stock").value("Stock is required"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());
    }
}