package com.dj1012h.researchpilot.exception;

import com.dj1012h.researchpilot.common.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception,
                                                       HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        List<String> fields = new ArrayList<>();
        List<Integer> inputLengths = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
            fields.add(fieldError.getField());
            inputLengths.add(inputLength(fieldError.getRejectedValue()));
            reasons.add(fieldError.getCode());
        }
        log.info(
                "event=request_validation_failed method={} path={} fields={} inputLengths={} reasons={}",
                request.getMethod(),
                request.getRequestURI(),
                fields,
                inputLengths,
                reasons
        );
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数校验失败", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception,
                                                              HttpServletRequest request) {
        log.info(
                "event=request_body_invalid method={} path={} reason=INVALID_JSON exceptionType={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getSimpleName()
        );
        return build(HttpStatus.BAD_REQUEST, "INVALID_JSON", "请求体不是有效的 JSON", request, Map.of());
    }

    @ExceptionHandler(ModelNotConfiguredException.class)
    ResponseEntity<ApiErrorResponse> handleModelNotConfigured(ModelNotConfiguredException exception,
                                                               HttpServletRequest request) {
        log.info(
                "event=model_not_configured method={} path={}",
                request.getMethod(),
                request.getRequestURI()
        );
        return build(
                HttpStatus.SERVICE_UNAVAILABLE,
                "MODEL_NOT_CONFIGURED",
                "聊天模型当前未配置或未启用",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(ModelInvocationException.class)
    ResponseEntity<ApiErrorResponse> handleModelInvocation(ModelInvocationException exception,
                                                            HttpServletRequest request) {
        return switch (exception.getFailureType()) {
            case AUTHENTICATION -> build(
                    HttpStatus.BAD_GATEWAY,
                    "MODEL_AUTHENTICATION_FAILED",
                    "模型服务认证失败",
                    request,
                    Map.of()
            );
            case TIMEOUT -> build(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "MODEL_TIMEOUT",
                    "模型服务响应超时",
                    request,
                    Map.of()
            );
            case UNAVAILABLE -> build(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MODEL_UNAVAILABLE",
                    "模型服务暂时不可用",
                    request,
                    Map.of()
            );
            case RATE_LIMITED -> build(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MODEL_RATE_LIMITED",
                    "模型服务请求受限，请稍后重试",
                    request,
                    Map.of()
            );
            case MODEL_NOT_FOUND -> build(
                    HttpStatus.BAD_GATEWAY,
                    "MODEL_NOT_FOUND",
                    "配置的模型不可用",
                    request,
                    Map.of()
            );
            case INVALID_PROVIDER_REQUEST -> build(
                    HttpStatus.BAD_GATEWAY,
                    "MODEL_REQUEST_REJECTED",
                    "模型服务拒绝了请求",
                    request,
                    Map.of()
            );
            case PROVIDER_ERROR -> build(
                    HttpStatus.BAD_GATEWAY,
                    "MODEL_INVOCATION_FAILED",
                    "模型调用失败",
                    request,
                    Map.of()
            );
        };
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error(
                "event=unhandled_request_error method={} path={} exceptionType={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getName(),
                exception
        );
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误", request, Map.of());
    }

    private int inputLength(Object rejectedValue) {
        return rejectedValue instanceof CharSequence value ? value.length() : -1;
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status,
                                                   String code,
                                                   String message,
                                                   HttpServletRequest request,
                                                   Map<String, String> details) {
        ApiErrorResponse error = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                details
        );
        return ResponseEntity.status(status).body(error);
    }
}
