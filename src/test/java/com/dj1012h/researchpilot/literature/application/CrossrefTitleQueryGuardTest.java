package com.dj1012h.researchpilot.literature.application;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CrossrefTitleQueryGuardTest {

    private final CrossrefTitleQueryGuard guard = new CrossrefTitleQueryGuard();

    @ParameterizedTest
    @MethodSource("rejectedTitles")
    void shouldRejectClearlyNonTitleInput(String title, CrossrefTitleQueryGuard.RejectionReason reason) {
        CrossrefTitleQueryGuard.Decision decision = guard.assess(title);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.rejectionReason()).isEqualTo(reason);
    }

    @ParameterizedTest
    @MethodSource("allowedTitles")
    void shouldAllowOrdinaryUnicodeResearchTitles(String title) {
        assertThat(guard.assess(title).allowed()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("unicodeLengths")
    void shouldCountUnicodeCodePointsRatherThanUtf16Units(String title, boolean allowed) {
        assertThat(guard.assess(title).allowed()).isEqualTo(allowed);
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> rejectedTitles() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(null, CrossrefTitleQueryGuard.RejectionReason.MISSING),
                org.junit.jupiter.params.provider.Arguments.of(" \n\t ", CrossrefTitleQueryGuard.RejectionReason.MISSING),
                org.junit.jupiter.params.provider.Arguments.of("?!—", CrossrefTitleQueryGuard.RejectionReason.NO_LETTER_OR_DIGIT),
                org.junit.jupiter.params.provider.Arguments.of("https://example.invalid/work", CrossrefTitleQueryGuard.RejectionReason.URL),
                org.junit.jupiter.params.provider.Arguments.of("{\"title\":\"x\"}", CrossrefTitleQueryGuard.RejectionReason.JSON),
                org.junit.jupiter.params.provider.Arguments.of("<?xml version=\"1.0\"?><work>title</work>", CrossrefTitleQueryGuard.RejectionReason.XML_OR_HTML),
                org.junit.jupiter.params.provider.Arguments.of("<html><body>x</body></html>", CrossrefTitleQueryGuard.RejectionReason.XML_OR_HTML),
                org.junit.jupiter.params.provider.Arguments.of("```json\n{\"x\":1}\n```", CrossrefTitleQueryGuard.RejectionReason.MARKDOWN_CODE_FENCE),
                org.junit.jupiter.params.provider.Arguments.of("A\u0000 title", CrossrefTitleQueryGuard.RejectionReason.CONTROL_CHARACTER),
                org.junit.jupiter.params.provider.Arguments.of("a".repeat(33), CrossrefTitleQueryGuard.RejectionReason.REPETITIVE)
        );
    }

    private static Stream<String> allowedTitles() {
        return Stream.of(
                "面向遥感变化检测的 Mamba 模型：方法与实验",
                "Can Mamba-2 detect change? A 2026 study",
                "A Long English Research Title on Robust Multi-Temporal Remote Sensing Change Detection with Foundation Models, "
                        + "Uncertainty-Aware Fusion, and Reproducible Evaluation Protocols",
                "(ViT-B/16) + ΔF = 3.14: 2D/3D change detection"
        );
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> unicodeLengths() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("😀a".repeat(256), true),
                org.junit.jupiter.params.provider.Arguments.of("😀a".repeat(256) + "b", false)
        );
    }
}
