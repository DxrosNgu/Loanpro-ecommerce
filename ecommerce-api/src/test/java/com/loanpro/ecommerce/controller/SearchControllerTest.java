package com.loanpro.ecommerce.controller;

import com.loanpro.ecommerce.domain.Category;
import com.loanpro.ecommerce.dto.response.PageResponse;
import com.loanpro.ecommerce.dto.response.ProductResponse;
import com.loanpro.ecommerce.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
@DisplayName("SearchController")
class SearchControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    ProductService productService;

    private PageResponse<ProductResponse> pageOf(ProductResponse... items) {
        return PageResponse.<ProductResponse>builder()
            .content(List.of(items)).page(0).size(12)
            .totalElements(items.length).totalPages(1).last(true).build();
    }

    private ProductResponse shoe() {
        return ProductResponse.builder()
            .id(1L).name("Running Shoes").sku("RS-001")
            .price(BigDecimal.valueOf(89.99)).stock(150)
            .category(Category.FOOTWEAR).build();
    }

    @Test @DisplayName("returns products for keyword query")
    void returnsProductsForKeyword() throws Exception {
        when(productService.search(eq("shoe"), isNull(), isNull(), isNull(), eq(0), eq(12)))
            .thenReturn(pageOf(shoe()));

        mockMvc.perform(get("/api/search").param("q", "shoe"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("Running Shoes"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test @DisplayName("returns empty page when nothing matches")
    void returnsEmptyPage() throws Exception {
        when(productService.search(eq("xyz"), isNull(), isNull(), isNull(), eq(0), eq(12)))
            .thenReturn(pageOf());

        mockMvc.perform(get("/api/search").param("q", "xyz"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test @DisplayName("passes category filter to service")
    void passesCategoryFilter() throws Exception {
        when(productService.search(isNull(), eq("FOOTWEAR"), isNull(), isNull(), eq(0), eq(12)))
            .thenReturn(pageOf(shoe()));

        mockMvc.perform(get("/api/search").param("category", "FOOTWEAR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].category").value("FOOTWEAR"));
    }

    @Test @DisplayName("passes price range filters to service")
    void passesPriceRange() throws Exception {
        when(productService.search(isNull(), isNull(),
                eq(new BigDecimal("10.00")), eq(new BigDecimal("100.00")), eq(0), eq(12)))
            .thenReturn(pageOf(shoe()));

        mockMvc.perform(get("/api/search")
                .param("minPrice", "10.00")
                .param("maxPrice", "100.00"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].price").value(89.99));
    }

    @Test @DisplayName("passes combined keyword + category + price to service")
    void passesCombinedFilters() throws Exception {
        when(productService.search(eq("shoe"), eq("FOOTWEAR"),
                eq(new BigDecimal("50.00")), eq(new BigDecimal("200.00")), eq(0), eq(12)))
            .thenReturn(pageOf(shoe()));

        mockMvc.perform(get("/api/search")
                .param("q", "shoe")
                .param("category", "FOOTWEAR")
                .param("minPrice", "50.00")
                .param("maxPrice", "200.00"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test @DisplayName("defaults to page 0 size 12 when no pagination params given")
    void defaultPagination() throws Exception {
        when(productService.search(isNull(), isNull(), isNull(), isNull(), eq(0), eq(12)))
            .thenReturn(pageOf());

        mockMvc.perform(get("/api/search"))
            .andExpect(status().isOk());
    }
}
