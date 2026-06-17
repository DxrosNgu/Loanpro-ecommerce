package com.loanpro.ecommerce.controller;

import com.loanpro.ecommerce.dto.response.CsvImportResult;
import com.loanpro.ecommerce.service.CsvImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "CSV Import", description = "Bulk import products from a CSV file")
public class CsvImportController {

    private final CsvImportService csvImportService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import products from a CSV file")
    public CsvImportResult importCsv(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) throw new IllegalArgumentException("File is empty");
        return csvImportService.importFile(file);
    }
}
