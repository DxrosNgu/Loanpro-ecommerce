package com.loanpro.ecommerce.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class CsvImportResult {
    private int imported;
    private int updated;
    private int skipped;
    private List<RowError> errors;

    @Data @Builder
    public static class RowError {
        private int row;
        private String sku;
        private String reason;
    }
}
