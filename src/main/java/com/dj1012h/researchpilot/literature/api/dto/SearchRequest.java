package com.dj1012h.researchpilot.literature.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * User-facing request for topic-based literature retrieval.
 *
 * <p>Explicit structured filters take precedence over constraints inferred from
 * {@code query}. Defaults and cross-field validation are applied later by the
 * search-plan validator.</p>
 */
public record SearchRequest(
        @NotBlank(message = "query 不能为空")
        @Size(max = 500, message = "query 不能超过 500 个字符")
        String query,

        @Min(value = 1900, message = "fromYear 不能早于 1900")
        Integer fromYear,

        @Min(value = 1900, message = "toYear 不能早于 1900")
        Integer toYear,

        @Min(value = 1, message = "limit 不能小于 1")
        @Max(value = 15, message = "limit 不能大于 15")
        Integer limit
) {
}
