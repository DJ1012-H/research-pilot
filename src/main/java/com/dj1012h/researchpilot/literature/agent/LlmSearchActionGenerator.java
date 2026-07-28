package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.common.ai.ModelInvoker;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Optional AI Services adapter; model availability remains guarded by ModelInvoker. */
@Component
public class LlmSearchActionGenerator implements SearchActionGenerator {

    private final ModelInvoker modelInvoker;

    public LlmSearchActionGenerator(ModelInvoker modelInvoker) {
        this.modelInvoker = Objects.requireNonNull(modelInvoker, "modelInvoker must not be null");
    }

    @Override
    public String generate(SearchActionContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return modelInvoker.invoke("search_action_decision", context.prompt(), (chatModel, input) ->
                AiServices.builder(SearchActionAiService.class)
                        .chatModel(chatModel)
                        .build()
                        .decide(input));
    }
}
