package com.dj1012h.researchpilot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        //@NotNull只能拦截null，不能拦截“”和空格
        @NotBlank(message = "message 不能为空")
        @Size(max = 4_000, message = "message 不能超过 4000 个字符")
        String message
) {
}
