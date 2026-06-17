package com.loanpro.ecommerce.controller;

import com.loanpro.ecommerce.dto.response.CsvImportResult;
import com.loanpro.ecommerce.service.CsvImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CsvImportController.class)
@DisplayName("CsvImportController")
class CsvImportControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CsvImportService csvImportService;

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "products.csv",
            "text/csv", content.getBytes());
    }

    @Test @DisplayName("POST /api/products/import returns import result")
    void returnsImportResult() throws Exception {
        CsvImportResult result = CsvImportResult.builder()
            .imported(45).updated(3).skipped(2)
            .errors(List.of(
                CsvImportResult.RowError.builder().row(3).sku("DL-007").reason("Stock is negative (-5)").build()
            )).build();

        when(csvImportService.importFile(any())).thenReturn(result);

        mockMvc.perform(multipart("/api/products/import")
                .file(csvFile("name,sku\nRunning Shoes,RS-001")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(45))
            .andExpect(jsonPath("$.updated").value(3))
            .andExpect(jsonPath("$.skipped").value(2))
            .andExpect(jsonPath("$.errors[0].sku").value("DL-007"))
            .andExpect(jsonPath("$.errors[0].reason").value("Stock is negative (-5)"));
    }

    @Test @DisplayName("POST /api/products/import returns 400 for empty file")
    void rejectsEmptyFile() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
            "file", "empty.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/products/import").file(empty))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("POST /api/products/import returns all-zero result for header-only CSV")
    void handlesHeaderOnlyCsv() throws Exception {
        when(csvImportService.importFile(any())).thenReturn(
            CsvImportResult.builder().imported(0).updated(0).skipped(0).errors(List.of()).build());

        mockMvc.perform(multipart("/api/products/import")
                .file(csvFile("name,sku,description,category,price,stock,weight_kg")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(0))
            .andExpect(jsonPath("$.errors").isEmpty());
    }
}
