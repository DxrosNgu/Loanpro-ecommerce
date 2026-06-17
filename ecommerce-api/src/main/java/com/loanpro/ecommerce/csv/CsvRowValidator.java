package com.loanpro.ecommerce.csv;

import com.loanpro.ecommerce.dto.response.CsvImportResult.RowError;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CsvRowValidator {

    @Data @Builder
    public static class ValidatedRow {
        private String name;
        private String sku;
        private String description;
        private String categoryRaw;
        private BigDecimal price;
        private Integer stock;
        private BigDecimal weightKg;
        private boolean valid;
        private List<String> warnings;
    }

    public static Result validate(String[] cols, int rowNumber) {
        List<RowError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (cols.length < 2) {
            return Result.rejected(rowNumber, null, "Row has too few columns");
        }

        String rawName = cols.length > 0 ? cols[0] : "";
        String rawSku  = cols.length > 1 ? cols[1] : "";
        String rawDesc = cols.length > 2 ? cols[2] : "";
        String rawCat  = cols.length > 3 ? cols[3] : "";
        String rawPrice= cols.length > 4 ? cols[4] : "";
        String rawStock= cols.length > 5 ? cols[5] : "";
        String rawWeight=cols.length > 6 ? cols[6] : "";

        if (rawName == null || rawName.isBlank()) {
            return Result.rejected(rowNumber, rawSku.isBlank() ? null : rawSku.trim(), "Name is blank");
        }

        String name = rawName.trim();
        if (name.isBlank()) {
            return Result.rejected(rowNumber, rawSku.isBlank() ? null : rawSku.trim(), "Name is whitespace only");
        }

        if (rawSku == null || rawSku.isBlank()) {
            return Result.rejected(rowNumber, null, "SKU is blank");
        }
        String sku = rawSku.trim();

        BigDecimal price;
        try {
            String cleanPrice = rawPrice.trim().replaceAll("[^\\d.]", "");
            if (cleanPrice.isEmpty() || cleanPrice.equals(".")) {
                return Result.rejected(rowNumber, sku, "Price is empty or invalid");
            }
            if (rawPrice.trim().equalsIgnoreCase("free")) {
                price = BigDecimal.ZERO;
                warnings.add("Price 'free' mapped to 0.00");
            } else {
                price = new BigDecimal(cleanPrice);
                if (!rawPrice.trim().equals(cleanPrice)) {
                    warnings.add("Price cleaned from '" + rawPrice.trim() + "' to " + price);
                }
            }
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                return Result.rejected(rowNumber, sku, "Price is negative");
            }
        } catch (NumberFormatException e) {
            return Result.rejected(rowNumber, sku, "Price '" + rawPrice.trim() + "' is not a valid number");
        }

        int stock;
        try {
            stock = Integer.parseInt(rawStock.trim());
            if (stock < 0) {
                return Result.rejected(rowNumber, sku, "Stock is negative (" + stock + ")");
            }
        } catch (NumberFormatException e) {
            return Result.rejected(rowNumber, sku, "Stock '" + rawStock.trim() + "' is not a valid integer");
        }

        BigDecimal weightKg = null;
        if (rawWeight != null && !rawWeight.isBlank()) {
            try {
                weightKg = new BigDecimal(rawWeight.trim());
                if (weightKg.compareTo(BigDecimal.ZERO) < 0) {
                    warnings.add("Weight is negative, stored as null");
                    weightKg = null;
                }
            } catch (NumberFormatException e) {
                warnings.add("Weight '" + rawWeight.trim() + "' is invalid, stored as null");
            }
        }

        ValidatedRow row = ValidatedRow.builder()
            .name(name)
            .sku(sku)
            .description(rawDesc)
            .categoryRaw(rawCat)
            .price(price)
            .stock(stock)
            .weightKg(weightKg)
            .valid(true)
            .warnings(warnings)
            .build();

        return Result.accepted(row);
    }

    @Data @Builder
    public static class Result {
        private ValidatedRow row;
        private RowError error;
        private boolean accepted;

        static Result accepted(ValidatedRow row) {
            return Result.builder().row(row).accepted(true).build();
        }

        static Result rejected(int rowNum, String sku, String reason) {
            return Result.builder()
                .error(RowError.builder().row(rowNum).sku(sku).reason(reason).build())
                .accepted(false)
                .build();
        }
    }
}
