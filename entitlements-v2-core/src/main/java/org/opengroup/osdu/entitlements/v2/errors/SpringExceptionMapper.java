package org.opengroup.osdu.entitlements.v2.errors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.partition.PartitionException;
import org.opengroup.osdu.entitlements.v2.validation.PartitionHeaderValidationService;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ValidationException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.stream.Collectors;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class SpringExceptionMapper extends ResponseEntityExceptionHandler {

    @Autowired
    private JaxRsDpsLog log;

    @ExceptionHandler(AppException.class)
    protected ResponseEntity<Object> handleAppException(AppException e) {
        if (e.getOriginalException() instanceof PartitionException) {
            handlePartitionException(e.getError());
        }

        return this.getErrorResponse(e);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<String> errorList = ex
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .collect(Collectors.toList());
        String errorMessage = String.join(", ", errorList);
        return this.getErrorResponse(new AppException(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), errorMessage, ex));
    }

    @Override
    @NonNull
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Bad Request", "Failed to read request", e));
    }

    @Override
    @NonNull
    protected ResponseEntity<Object> handleTypeMismatch(
            @NonNull TypeMismatchException e,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                        "Invalid parameters were given on request", e));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
        WebRequest request) {
        return this.getErrorResponse(new AppException(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), "Invalid filter"));
    }

    /**
     * Catch-all for Spring MVC exceptions not given a dedicated override
     * (415, 405, 406, MissingPathVariable, etc.).
     * <p>
     * {@code ResponseEntityExceptionHandler} always builds {@code ProblemDetail}
     * bodies, so {@code spring.mvc.problemdetails.enabled=false} has no effect
     * when this advice is present. Route everything through {@link AppError}.
     */
    @Override
    @Nullable
    protected ResponseEntity<Object> handleExceptionInternal(
            @NonNull Exception ex,
            @Nullable Object body,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode,
            @NonNull WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            HttpServletResponse response = servletWebRequest.getResponse();
            if (response != null && response.isCommitted()) {
                return null;
            }
        }

        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        if (resolved == null) {
            resolved = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getMessage() != null ? ex.getMessage() : resolved.getReasonPhrase();
        return this.getErrorResponse(
                new AppException(statusCode.value(), resolved.getReasonPhrase(), message, ex));
    }

    @ExceptionHandler({ValidationException.class, JsonProcessingException.class, UnrecognizedPropertyException.class})
    protected ResponseEntity<Object> handleValidationException(Exception e) {
        return this.getErrorResponse(new AppException(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), e.getMessage(), e));
    }

    @ExceptionHandler({AccessDeniedException.class})
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        return this.getErrorResponse(new AppException(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(), ex.getMessage(), ex));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handleGeneralException(Exception e) {
        if (e instanceof BeanCreationException beanCreationException && beanCreationException.getBeanName().equalsIgnoreCase("scopedTarget.getTenantInfo")) {
            return this.getErrorResponse(new AppException(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(), PartitionHeaderValidationService.INVALID_DP_HEADER_ERROR));
        }
        else if(e instanceof IllegalArgumentException) {
        	return this.getErrorResponse(new AppException(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), PartitionHeaderValidationService.INVALID_ARGUMENT_ERROR));
        }
        return this.getErrorResponse(new AppException(HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "An unknown error has occurred", e));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Object> handleIOException(IOException e) {
        if (StringUtils.containsIgnoreCase(ExceptionUtils.getRootCauseMessage(e), "Broken pipe")) {
            this.log.warning("Client closed the connection while request still being processed");
            return null;
        } else {
            return this.getErrorResponse(
                    new AppException(HttpStatus.SERVICE_UNAVAILABLE.value(), "Unknown error", e.getMessage(), e));
        }
    }

    private void handlePartitionException(AppError error) {
        error.setCode(HttpStatus.UNAUTHORIZED.value());
        error.setReason("Partition Service issue");
        error.setMessage("Cannot authorize user in requested partition");
    }

    private ResponseEntity<Object> getErrorResponse(AppException e) {
        if (e.getCause() instanceof Exception originalException) {
            this.log.error(originalException.getMessage(), originalException);
        }

        if (e.getError().getCode() > 499) {
            this.log.error(e.getError().getMessage(), e);
        } else {
            this.log.warning(e.getError().getMessage(), e);
        }

        return new ResponseEntity<>(e.getError(), HttpStatus.resolve(e.getError().getCode()));
    }
}