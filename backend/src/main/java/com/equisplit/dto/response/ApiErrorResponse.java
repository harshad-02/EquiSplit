package com.equisplit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private String message;
    private Map<String, String> errors;

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(String message) {
        this.message = message;
    }

    public ApiErrorResponse(String message, Map<String, String> errors) {
        this.message = message;
        this.errors = errors;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    public void setErrors(Map<String, String> errors) {
        this.errors = errors;
    }
}
