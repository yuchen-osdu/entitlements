package org.opengroup.osdu.entitlements.v2.errors;

import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class SpringExceptionMapperTest {

    @InjectMocks
    private SpringExceptionMapper springExceptionMapper;

    @Mock
    private JaxRsDpsLog log;

    @Test
    public void should_returnNullResponse_when_BrokenPipeIOExceptionIsCaptured() {
        IOException ioException = new IOException("Broken pipe");

        ResponseEntity response = springExceptionMapper.handleIOException(ioException);

        Assert.assertNull(response);
    }

    @Test
    public void should_returnServiceUnavailable_when_IOExceptionIsCaptured() {
        IOException ioException = new IOException("Not broken yet");

        ResponseEntity response = springExceptionMapper.handleIOException(ioException);

        Assert.assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, response.getStatusCodeValue());
    }

    @Test
    public void should_returnBadRequest_when_HttpMessageNotReadableExceptionIsCaptured() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "Failed to read request",
                new MockHttpInputMessage("".getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<Object> response = springExceptionMapper.handleHttpMessageNotReadable(
                exception, new HttpHeaders(), org.springframework.http.HttpStatus.BAD_REQUEST, mock(WebRequest.class));

        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCodeValue());
        Assert.assertTrue(response.getBody() instanceof AppError);
    }

    @Test
    public void should_returnBadRequest_when_TypeMismatchExceptionIsCaptured() {
        TypeMismatchException exception = new TypeMismatchException("not-a-number", Integer.class);

        ResponseEntity<Object> response = springExceptionMapper.handleTypeMismatch(
                exception, new HttpHeaders(), org.springframework.http.HttpStatus.BAD_REQUEST, mock(WebRequest.class));

        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCodeValue());
        Assert.assertTrue(response.getBody() instanceof AppError);
    }

    @Test
    public void should_returnAppError_when_methodNotSupported() {
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException(HttpMethod.PUT.name());

        ResponseEntity<Object> response = springExceptionMapper.handleExceptionInternal(
                exception, null, new HttpHeaders(),
                org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED, mock(WebRequest.class));

        Assert.assertEquals(HttpStatus.SC_METHOD_NOT_ALLOWED, response.getStatusCodeValue());
        Assert.assertTrue(response.getBody() instanceof AppError);
        AppError error = (AppError) response.getBody();
        Assert.assertEquals(HttpStatus.SC_METHOD_NOT_ALLOWED, error.getCode());
        Assert.assertEquals("Method Not Allowed", error.getReason());
    }

    @Test
    public void should_returnAppError_when_mediaTypeNotSupported() {
        HttpMediaTypeNotSupportedException exception =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<Object> response = springExceptionMapper.handleExceptionInternal(
                exception, null, new HttpHeaders(),
                org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, mock(WebRequest.class));

        Assert.assertEquals(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, response.getStatusCodeValue());
        Assert.assertTrue(response.getBody() instanceof AppError);
        AppError error = (AppError) response.getBody();
        Assert.assertEquals(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, error.getCode());
        Assert.assertEquals("Unsupported Media Type", error.getReason());
    }
}
