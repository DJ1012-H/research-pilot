package com.dj1012h.researchpilot.service.impl;

import com.dj1012h.researchpilot.config.AiProperties;
import com.dj1012h.researchpilot.exception.ModelFailureType;
import com.dj1012h.researchpilot.exception.ModelInvocationException;
import com.dj1012h.researchpilot.exception.ModelNotConfiguredException;
import com.dj1012h.researchpilot.service.ChatService;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final AiProperties aiProperties;

    public ChatServiceImpl(ObjectProvider<ChatModel> chatModelProvider, AiProperties aiProperties) {
        this.chatModelProvider = chatModelProvider;
        this.aiProperties = aiProperties;
    }

    @Override
    public String chat(String message) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new ModelNotConfiguredException(
                    "聊天模型尚未启用，请配置 LLM_BASE_URL、LLM_API_KEY、LLM_MODEL_NAME，并设置 LLM_ENABLED=true"
            );
        }

        long startNanos = System.nanoTime();
        try {
            return chatModel.chat(message);
        } catch (AuthenticationException exception) {
            throw knownFailure(ModelFailureType.AUTHENTICATION, exception, message, startNanos);
        } catch (TimeoutException exception) {
            throw knownFailure(ModelFailureType.TIMEOUT, exception, message, startNanos);
        } catch (RateLimitException exception) {
            throw knownFailure(ModelFailureType.RATE_LIMITED, exception, message, startNanos);
        } catch (ModelNotFoundException exception) {
            throw knownFailure(ModelFailureType.MODEL_NOT_FOUND, exception, message, startNanos);
        } catch (InvalidRequestException exception) {
            throw knownFailure(ModelFailureType.INVALID_PROVIDER_REQUEST, exception, message, startNanos);
        } catch (InternalServerException | UnresolvedModelServerException exception) {
            throw knownFailure(ModelFailureType.UNAVAILABLE, exception, message, startNanos);
        } catch (LangChain4jException exception) {
            throw knownFailure(ModelFailureType.PROVIDER_ERROR, exception, message, startNanos);
        } catch (RuntimeException exception) {
            if (hasCause(exception, IOException.class)) {
                throw knownFailure(ModelFailureType.UNAVAILABLE, exception, message, startNanos);
            }
            throw exception;
        }
    }

    private ModelInvocationException knownFailure(ModelFailureType failureType,
                                                   RuntimeException exception,
                                                   String message,
                                                   long startNanos) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        int inputLength = message == null ? -1 : message.length();

        log.warn(
                "event=model_call_failed model={} inputLength={} durationMs={} failureType={} "
                        + "exceptionType={} rootCauseType={}",
                modelNameForLog(),
                inputLength,
                durationMs,
                failureType,
                exception.getClass().getSimpleName(),
                rootCause(exception).getClass().getSimpleName()
        );

        return new ModelInvocationException(failureType, exception);
    }

    private String modelNameForLog() {
        String modelName = aiProperties.getModelName();
        if (modelName == null || modelName.isBlank()) {
            return "unknown";
        }
        return modelName.replace('\r', '_').replace('\n', '_');
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
}
