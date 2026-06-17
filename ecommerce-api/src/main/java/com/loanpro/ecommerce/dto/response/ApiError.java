package com.loanpro.ecommerce.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data @Builder
public class ApiError {
    private int status;
    private String message;
    private Instant timestamp;
    private Map<String, String> fieldErrors;
}
