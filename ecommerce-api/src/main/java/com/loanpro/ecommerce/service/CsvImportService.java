package com.loanpro.ecommerce.service;

import com.loanpro.ecommerce.csv.CsvRowValidator;
import com.loanpro.ecommerce.csv.CsvRowValidator.Result;
import com.loanpro.ecommerce.domain.Category;
import com.loanpro.ecommerce.domain.Product;
import com.loanpro.ecommerce.dto.response.CsvImportResult;
import com.loanpro.ecommerce.dto.response.CsvImportResult.RowError;
import com.loanpro.ecommerce.repository.ProductRepository;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.RequiredArgsConstructor;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final ProductRepository repo;
    private static final PolicyFactory SANITIZER = Sanitizers.FORMATTING.and(Sanitizers.LINKS);

    @Transactional
    public CsvImportResult importFile(MultipartFile file) throws IOException, CsvException {
        int imported = 0, updated = 0, skipped = 0;
        List<RowError> errors = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) return empty();

            for (int i = 1; i < rows.size(); i++) {
                String[] cols = rows.get(i);

                if (isBlankRow(cols)) { skipped++; continue; }

                Result validation = CsvRowValidator.validate(cols, i + 1);

                if (!validation.isAccepted()) {
                    errors.add(validation.getError());
                    continue;
                }

                CsvRowValidator.ValidatedRow r = validation.getRow();
                String cleanName = sanitize(r.getName());
                String cleanDesc = sanitize(r.getDescription());
                String sku = r.getSku().toUpperCase();

                var existing = repo.findBySku(sku);
                if (existing.isPresent()) {
                    Product p = existing.get();
                    p.setDeleted(false);
                    p.setName(cleanName);
                    p.setDescription(cleanDesc);
                    p.setCategory(Category.fromCsvValue(r.getCategoryRaw()));
                    p.setPrice(r.getPrice());
                    p.setStock(r.getStock());
                    p.setWeightKg(r.getWeightKg());
                    repo.save(p);
                    updated++;
                } else {
                    Product p = Product.builder()
                        .name(cleanName)
                        .sku(sku)
                        .description(cleanDesc)
                        .category(Category.fromCsvValue(r.getCategoryRaw()))
                        .price(r.getPrice())
                        .stock(r.getStock())
                        .weightKg(r.getWeightKg())
                        .build();
                    repo.save(p);
                    imported++;
                }
            }
        }

        return CsvImportResult.builder()
            .imported(imported)
            .updated(updated)
            .skipped(skipped)
            .errors(errors)
            .build();
    }

    private String sanitize(String input) {
        if (input == null) return null;
        return SANITIZER.sanitize(input).trim();
    }

    private boolean isBlankRow(String[] cols) {
        if (cols == null || cols.length == 0) return true;
        for (String col : cols) {
            if (col != null && !col.isBlank()) return false;
        }
        return true;
    }

    private CsvImportResult empty() {
        return CsvImportResult.builder().imported(0).updated(0).skipped(0)
            .errors(List.of()).build();
    }
}
