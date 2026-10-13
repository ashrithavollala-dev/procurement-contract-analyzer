package com.company.contractanalyzer.exception;

import com.company.contractanalyzer.dto.response.ErrorResponse;
import com.company.contractanalyzer.enums.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Translates every exception into the single error payload
 * {@code {timestamp, status, error, code, message, path, details?}}. Clients branch on
 * {@code code}. Stack traces and exception messages from libraries never reach the client.
 * Unexpected errors are logged with their type and the client sees INTERNAL_ERROR.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_MESSAGE = "Request validation failed";

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e, HttpServletRequest request) {
        if (e.getStatus().is5xxServerError()) {
            log.warn("{} {} -> {} {}", request.getMethod(), request.getRequestURI(), e.getStatus().value(), e.getCode());
        }
        return build(e.getStatus(), e.getCode(), e.getMessage(), e.getDetails(), request);
    }

    /** @Valid on @RequestBody (MethodArgumentNotValidException) and on @ModelAttribute (BindException). */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(BindException e, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(error.getField(), error.isBindingFailure() ? "has an invalid value" : error.getDefaultMessage());
        }
        e.getBindingResult().getGlobalErrors().forEach(error -> details.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, VALIDATION_MESSAGE, details, request);
    }

    /** Constraint annotations on @RequestParam / @PathVariable (Spring MVC built-in method validation). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e,
                                                                HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        e.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            String message = result.getResolvableErrors().stream()
                    .map(MessageSourceResolvable::getDefaultMessage)
                    .findFirst().orElse("is invalid");
            details.putIfAbsent(name == null ? "parameter" : name, message);
        });
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, VALIDATION_MESSAGE, details, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e,
                                                                   HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath().toString();
            details.putIfAbsent(path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path, v.getMessage());
        });
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, VALIDATION_MESSAGE, details, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                            HttpServletRequest request) {
        String expected = e.getRequiredType() == null ? "a valid value" : "a valid " + e.getRequiredType().getSimpleName();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, VALIDATION_MESSAGE,
                Map.of(e.getName(), "must be " + expected), request);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> handleMissing(Exception e, HttpServletRequest request) {
        String name = e instanceof MissingServletRequestParameterException p ? p.getParameterName()
                : ((MissingServletRequestPartException) e).getRequestPartName();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, VALIDATION_MESSAGE,
                Map.of(name, "is required"), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Malformed request body (invalid JSON or an unknown enum value)", Map.of(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_TOO_LARGE, "The file exceeds the maximum upload size",
                Map.of(), request);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Malformed multipart request",
                Map.of("file", "could not be read"), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        boolean upload = request.getRequestURI().endsWith("/contracts") && "POST".equals(request.getMethod());
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                upload ? ErrorCode.UNSUPPORTED_FILE_TYPE : ErrorCode.VALIDATION_FAILED,
                upload ? "Upload the PDF as multipart/form-data" : "Unsupported content type", Map.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.VALIDATION_FAILED,
                "HTTP method " + e.getMethod() + " is not supported here", Map.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "No endpoint at this path", Map.of(), request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleConflict(Exception e, HttpServletRequest request) {
        log.info("{} {} -> 409 ({})", request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());
        return build(HttpStatus.CONFLICT, ErrorCode.VALIDATION_FAILED,
                "The request conflicts with the current state of the data; reload and retry", Map.of(), request);
    }

    /** The client went away (e.g. closed the tab during a long Ask call); nothing can be written. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientGone(AsyncRequestNotUsableException e) {
        log.debug("Client disconnected before the response was written");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled error on {} {} (type={})", request.getMethod(), request.getRequestURI(),
                e.getClass().getName());
        log.debug("Unhandled error detail", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred",
                Map.of(), request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, ErrorCode code, String message,
                                                Map<String, ?> details, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(Instant.now(clock), status.value(), status.getReasonPhrase(),
                code.name(), message, request.getRequestURI(), new LinkedHashMap<>(details));
        return ResponseEntity.status(status).body(body);
    }
}
