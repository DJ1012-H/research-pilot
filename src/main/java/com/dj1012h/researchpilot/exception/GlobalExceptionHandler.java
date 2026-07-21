package com.dj1012h.researchpilot.exception;

import com.dj1012h.researchpilot.common.response.ApiErrorResponse;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexApiException;
import com.dj1012h.researchpilot.integration.crossref.CrossrefApiException;
import com.dj1012h.researchpilot.literature.application.SearchPlanGenerationException;
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
                "模型服务当前未配置或未启用",
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

    @ExceptionHandler(OpenAlexApiException.class)
    ResponseEntity<ApiErrorResponse> handleOpenAlex(OpenAlexApiException exception,
                                                     HttpServletRequest request) {
        return switch (exception.getFailureType()) {
            case DISABLED, API_KEY_MISSING -> build(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OPENALEX_NOT_CONFIGURED",
                    "OpenAlex 检索服务未配置或未启用",
                    request,
                    Map.of()
            );
            case TIMEOUT -> build(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "OPENALEX_TIMEOUT",
                    "OpenAlex 检索响应超时",
                    request,
                    Map.of()
            );
            case RATE_LIMITED -> build(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OPENALEX_RATE_LIMITED",
                    "OpenAlex 请求受限，请稍后重试",
                    request,
                    Map.of()
            );
            case CLIENT_ERROR -> build(
                    HttpStatus.BAD_GATEWAY,
                    "OPENALEX_REQUEST_REJECTED",
                    "OpenAlex 拒绝了检索请求",
                    request,
                    Map.of()
            );
            case SERVER_ERROR, TRANSPORT_ERROR -> build(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OPENALEX_UNAVAILABLE",
                    "OpenAlex 检索服务暂时不可用",
                    request,
                    Map.of()
            );
            case EMPTY_RESPONSE, INVALID_RESPONSE -> build(
                    HttpStatus.BAD_GATEWAY,
                    "OPENALEX_INVALID_RESPONSE",
                    "OpenAlex 返回了无效响应",
                    request,
                    Map.of()
            );
        };
    }

    @ExceptionHandler(CrossrefApiException.class)
    ResponseEntity<ApiErrorResponse> handleCrossref(CrossrefApiException exception,
                                                     HttpServletRequest request) {
        return switch (exception.getFailureType()) {
            case DISABLED, MAILTO_MISSING, USER_AGENT_MISSING -> build(
                    HttpStatus.SERVICE_UNAVAILABLE, "CROSSREF_NOT_CONFIGURED",
                    "Crossref 服务未配置或未启用", request, Map.of());
            case TIMEOUT -> build(HttpStatus.GATEWAY_TIMEOUT, "CROSSREF_TIMEOUT",
                    "Crossref 响应超时", request, Map.of());
            case RATE_LIMITED -> build(HttpStatus.SERVICE_UNAVAILABLE, "CROSSREF_RATE_LIMITED",
                    "Crossref 请求受限，请稍后重试", request, Map.of());
            case UNAUTHORIZED, FORBIDDEN, CLIENT_ERROR, INVALID_REQUEST -> build(
                    HttpStatus.BAD_GATEWAY, "CROSSREF_REQUEST_REJECTED",
                    "Crossref 拒绝了请求", request, Map.of());
            case SERVER_ERROR, TRANSPORT_ERROR, INTERRUPTED -> build(
                    HttpStatus.SERVICE_UNAVAILABLE, "CROSSREF_UNAVAILABLE",
                    "Crossref 服务暂时不可用", request, Map.of());
            case EMPTY_RESPONSE, INVALID_RESPONSE -> build(
                    HttpStatus.BAD_GATEWAY, "CROSSREF_INVALID_RESPONSE",
                    "Crossref 返回了无效响应", request, Map.of());
            case NOT_FOUND -> build(HttpStatus.BAD_GATEWAY, "CROSSREF_NOT_FOUND",
                    "Crossref 未找到该记录", request, Map.of());
        };
    }

    @ExceptionHandler(SearchPlanGenerationException.class)
    ResponseEntity<ApiErrorResponse> handleSearchPlanGeneration(
            SearchPlanGenerationException exception,
            HttpServletRequest request
    ) {
        List<String> codes = exception.getIssues().stream()
                .map(issue -> issue.code())
                .distinct()
                .toList();
        log.info(
                "event=search_plan_generation_rejected method={} path={} stage={} codes={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getFinalStage(),
                codes
        );
        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "SEARCH_PLAN_GENERATION_FAILED",
                "无法根据请求生成有效的文献检索计划",
                request,
                Map.of("stage", exception.getFinalStage().name(), "codes", String.join(",", codes))
        );
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
