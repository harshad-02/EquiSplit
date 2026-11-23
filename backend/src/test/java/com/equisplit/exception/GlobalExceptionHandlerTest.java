package com.equisplit.exception;

import com.equisplit.dto.response.ApiErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("ApiException is properly translated with status and message")
    void testApiException() {
        BadRequestException badRequest = new BadRequestException("Invalid data provided");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleApiException(badRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid data provided", response.getBody().getMessage());
    }

    @Test
    @DisplayName("MethodArgumentNotValidException returns 400 and structured errors map")
    void testValidationException() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "must be a valid email address"));
        bindingResult.addError(new FieldError("target", "password", "must not be blank"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getErrors());
        assertEquals("must be a valid email address", response.getBody().getErrors().get("email"));
        assertEquals("must not be blank", response.getBody().getErrors().get("password"));
    }

    @Test
    @DisplayName("HttpMessageNotReadableException returns 400 with clean message")
    void testMalformedJsonException() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Malformed JSON input");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleHttpMessageNotReadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid or malformed JSON request", response.getBody().getMessage());
        assertNull(response.getBody().getErrors());
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException returns 400 with param name")
    void testTypeMismatchException() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", UUID.class, "groupId", null, new IllegalArgumentException()
        );
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid parameter: groupId", response.getBody().getMessage());
    }

    @Test
    @DisplayName("BadCredentialsException returns 401 with standard message")
    void testBadCredentials() {
        BadCredentialsException ex = new BadCredentialsException("Bad creds");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleBadCredentialsException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid email or password", response.getBody().getMessage());
    }

    @Test
    @DisplayName("AccessDeniedException returns 403 with Access denied message")
    void testAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Denied");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleAccessDeniedException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Access denied", response.getBody().getMessage());
    }

    @Test
    @DisplayName("DataIntegrityViolationException for email returns 409 Email already registered")
    void testDataIntegrityEmail() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("duplicate key value violates unique constraint \"users_email_key\"");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDataIntegrityViolation(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Email already registered", response.getBody().getMessage());
    }

    @Test
    @DisplayName("DataIntegrityViolationException for duplicate member returns 409")
    void testDataIntegrityDuplicateMember() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("duplicate key value violates unique constraint \"group_members_pkey\"");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDataIntegrityViolation(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Duplicate entry or conflict", response.getBody().getMessage());
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException returns 405 Method Not Allowed")
    void testMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PATCH");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleMethodNotSupported(ex);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("PATCH"));
    }

    @Test
    @DisplayName("Generic Exception returns 500 without leaking stack trace or internals")
    void testGenericException() {
        RuntimeException ex = new RuntimeException("Sensitive database connection details postgres://admin:pass@host:5432");
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal server error", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("postgres"));
        assertNull(response.getBody().getErrors());
    }
}
