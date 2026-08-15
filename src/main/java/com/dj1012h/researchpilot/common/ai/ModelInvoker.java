package com.dj1012h.researchpilot.common.ai;

import com.dj1012h.researchpilot.config.AiProperties;
import com.dj1012h.researchpilot.exception.ModelFailureType;
import com.dj1012h.researchpilot.exception.ModelInvocationException;
import com.dj1012h.researchpilot.exception.ModelNotConfiguredException;
import com.dj1012h.researchpilot.observability.LiteratureObservationMetrics;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Shared boundary for invoking the configured language model.
 *
 * <p>The invoker deliberately logs only operation metadata. Prompts, model
 * responses and provider exception messages may contain sensitive data and are
 * therefore never written to logs here.</p>
 */
@Component
public class ModelInvoker {

    private static final Logger log = LoggerFactory.getLogger(ModelInvoker.class);

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final AiProperties aiProperties;
    private final LiteratureObservationMetrics metrics;

    public ModelInvoker(ObjectProvider<ChatModel> chatModelProvider, AiProperties aiProperties) {
        this(chatModelProvider, aiProperties, LiteratureObservationMetrics.noop());
    }

    @Autowired
    public ModelInvoker(
            ObjectProvider<ChatModel> chatModelProvider,
            AiProperties aiProperties,
            LiteratureObservationMetrics metrics
    ) {
        this.chatModelProvider = chatModelProvider;
        this.aiProperties = aiProperties;
        this.metrics = metrics;
    }

    public String invoke(String operation, String input) {
        return invoke(operation, input, ChatModel::chat);
    }

    /**
     * Invokes the standard chat path while retaining provider-reported token
     * usage for bounded, non-content cost observations.
     */
    public ModelInvocationResult invokeWithUsage(String operation, String input) {
        ChatResponse response = invoke(operation, input,
                (chatModel, prompt) -> requireAssistantText(
                        chatModel.chat(UserMessage.from(prompt))));
        return resultWithUsage(operation, response);
    }

    /**
     * Invokes one provider JSON-mode request. The provider guarantees JSON
     * syntax only; callers must still apply their own schema and business
     * validation and fail closed on empty or invalid content.
     */
    public ModelInvocationResult invokeJsonWithUsage(
            String operation,
            String input,
            int maxOutputTokens
    ) {
        if (maxOutputTokens < 1 || maxOutputTokens > 2_000) {
            throw new IllegalArgumentException("maxOutputTokens must be between 1 and 2000");
        }
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from(input))
                .responseFormat(ResponseFormat.JSON)
                .temperature(0.0)
                .maxOutputTokens(maxOutputTokens)
                .build();
        ChatResponse response = invoke(operation, input,
                (chatModel, ignored) -> requireAssistantText(chatModel.chat(request)));
        return resultWithUsage(operation, response);
    }

    private ModelInvocationResult resultWithUsage(String operation, ChatResponse response) {
        TokenUsage usage = response.tokenUsage();
        Integer inputTokens = usage == null ? null : usage.inputTokenCount();
        Integer outputTokens = usage == null ? null : usage.outputTokenCount();
        Integer totalTokens = usage == null ? null : usage.totalTokenCount();
        log.info(
                "event=model_usage_observed operation={} model={} inputTokens={} outputTokens={} totalTokens={}",
                valueForLog(operation),
                modelNameForLog(),
                safeTokenCount(inputTokens),
                safeTokenCount(outputTokens),
                safeTokenCount(totalTokens)
        );
        return new ModelInvocationResult(response.aiMessage().text(), inputTokens, outputTokens, totalTokens);
    }

    private ChatResponse requireAssistantText(ChatResponse response) {
        if (response == null
                || response.aiMessage() == null
                || response.aiMessage().text() == null
                || response.aiMessage().text().isBlank()) {
            throw new EmptyModelResponseException();
        }
        return response;
    }

    /**
     * Invokes a provider-backed operation while preserving the application's
     * single model availability and exception-mapping boundary.
     */
    public <T> T invoke(String operation, String input, ModelCall<T> call) {
        long startNanos = System.nanoTime();
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            metrics.recordModel(operation, "failed", "NOT_CONFIGURED", java.time.Duration.ofMillis(elapsedMillis(startNanos)));
            throw new ModelNotConfiguredException(
                    "聊天模型尚未启用，请配置 LLM_BASE_URL、LLM_API_KEY、LLM_MODEL_NAME，并设置 LLM_ENABLED=true"
            );
        }

        try {
            T output = call.execute(chatModel, input);
            log.info(
                    "event=model_call_succeeded operation={} model={} inputLength={} durationMs={}",
                    valueForLog(operation),
                    modelNameForLog(),
                    inputLength(input),
                    elapsedMillis(startNanos)
            );
            metrics.recordModel(operation, "succeeded", null, java.time.Duration.ofMillis(elapsedMillis(startNanos)));
            return output;
        } catch (AuthenticationException exception) {
            throw knownFailure(ModelFailureType.AUTHENTICATION, exception, operation, input, startNanos);
        } catch (TimeoutException exception) {
            throw knownFailure(ModelFailureType.TIMEOUT, exception, operation, input, startNanos);
        } catch (RateLimitException exception) {
            throw knownFailure(ModelFailureType.RATE_LIMITED, exception, operation, input, startNanos);
        } catch (ModelNotFoundException exception) {
            throw knownFailure(ModelFailureType.MODEL_NOT_FOUND, exception, operation, input, startNanos);
        } catch (InvalidRequestException exception) {
            throw knownFailure(ModelFailureType.INVALID_PROVIDER_REQUEST, exception, operation, input, startNanos);
        } catch (EmptyModelResponseException exception) {
            throw knownFailure(ModelFailureType.EMPTY_RESPONSE, exception, operation, input, startNanos);
        } catch (InternalServerException | UnresolvedModelServerException exception) {
            throw knownFailure(ModelFailureType.UNAVAILABLE, exception, operation, input, startNanos);
        } catch (LangChain4jException exception) {
            throw knownFailure(ModelFailureType.PROVIDER_ERROR, exception, operation, input, startNanos);
        } catch (RuntimeException exception) {
            if (hasCause(exception, IOException.class)) {
                throw knownFailure(ModelFailureType.UNAVAILABLE, exception, operation, input, startNanos);
            }
            throw exception;
        }
    }

    @FunctionalInterface
    public interface ModelCall<T> {
        T execute(ChatModel chatModel, String input);
    }

    private ModelInvocationException knownFailure(ModelFailureType failureType,
                                                   RuntimeException exception,
                                                   String operation,
                                                   String input,
                                                   long startNanos) {
        log.warn(
                "event=model_call_failed operation={} model={} inputLength={} durationMs={} failureType={} "
                        + "exceptionType={} rootCauseType={}",
                valueForLog(operation),
                modelNameForLog(),
                inputLength(input),
                elapsedMillis(startNanos),
                failureType,
                exception.getClass().getSimpleName(),
                rootCause(exception).getClass().getSimpleName()
        );
        metrics.recordModel(operation, "failed", failureType.name(), java.time.Duration.ofMillis(elapsedMillis(startNanos)));
        return new ModelInvocationException(failureType, exception);
    }

    private int inputLength(String input) {
        return input == null ? -1 : input.length();
    }

    private long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private String modelNameForLog() {
        return valueForLog(aiProperties.getModelName());
    }

    private String valueForLog(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replace('\r', '_').replace('\n', '_');
    }

    private String safeTokenCount(Integer value) {
        return value == null ? "unknown" : Integer.toString(value);
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return false;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static final class EmptyModelResponseException extends RuntimeException {
        private EmptyModelResponseException() {
            super("model response did not contain assistant text");
        }
    }
}
