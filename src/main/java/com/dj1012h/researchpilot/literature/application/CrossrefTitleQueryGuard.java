package com.dj1012h.researchpilot.literature.application;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Rejects clearly non-title values before they can consume a Crossref request budget. */
@Component
public class CrossrefTitleQueryGuard {

    static final int MAX_CODE_POINTS = 512;
    private static final int REPETITION_MIN_CODE_POINTS = 32;
    private static final Pattern URL = Pattern.compile("(?i)^https?://\\S+$");
    private static final Pattern MARKDOWN_FENCE = Pattern.compile("(?s)^```.*```$");
    private static final Pattern HTML_OR_XML = Pattern.compile(
            "(?is)^(?:<\\?xml\\b.*\\?>.*|<!doctype\\s+html\\b.*|<html(?:\\s|>).*|<([a-z][a-z0-9:-]*)(?:\\s[^>]*)?>.*</\\1\\s*>)$"
    );

    public Decision assess(String rawTitle) {
        if (rawTitle == null) return Decision.rejected(RejectionReason.MISSING);
        String normalized = normalize(rawTitle);
        if (normalized.isEmpty()) return Decision.rejected(RejectionReason.MISSING);
        if (containsControl(rawTitle)) return Decision.rejected(RejectionReason.CONTROL_CHARACTER);
        if (normalized.codePointCount(0, normalized.length()) > MAX_CODE_POINTS) {
            return Decision.rejected(RejectionReason.TOO_LONG);
        }
        if (normalized.codePoints().noneMatch(Character::isLetterOrDigit)) {
            return Decision.rejected(RejectionReason.NO_LETTER_OR_DIGIT);
        }
        if (URL.matcher(normalized).matches()) return Decision.rejected(RejectionReason.URL);
        if (looksLikeJson(normalized)) return Decision.rejected(RejectionReason.JSON);
        if (HTML_OR_XML.matcher(normalized).matches()) return Decision.rejected(RejectionReason.XML_OR_HTML);
        if (MARKDOWN_FENCE.matcher(normalized).matches()) return Decision.rejected(RejectionReason.MARKDOWN_CODE_FENCE);
        if (isDominatedByOneCodePoint(normalized)) return Decision.rejected(RejectionReason.REPETITIVE);
        return Decision.allowed(normalized);
    }

    static String normalize(String value) {
        StringBuilder normalized = new StringBuilder();
        boolean pendingSpace = false;
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                if (normalized.length() > 0) pendingSpace = true;
            } else {
                if (pendingSpace) normalized.append(' ');
                normalized.appendCodePoint(codePoint);
                pendingSpace = false;
            }
        }
        return normalized.toString();
    }

    private boolean containsControl(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.getType(codePoint) == Character.CONTROL
                && !Character.isWhitespace(codePoint));
    }

    private boolean looksLikeJson(String value) {
        return (value.startsWith("{") && value.endsWith("}") && value.contains("\":"))
                || (value.startsWith("[") && value.endsWith("]") && value.contains("\""));
    }

    private boolean isDominatedByOneCodePoint(String value) {
        int count = value.codePointCount(0, value.length());
        if (count < REPETITION_MIN_CODE_POINTS) return false;
        Map<Integer, Integer> frequencies = new HashMap<>();
        int highest = value.codePoints().map(codePoint -> frequencies.merge(codePoint, 1, Integer::sum))
                .max().orElse(0);
        return highest * 100 > count * 80;
    }

    public record Decision(String normalizedTitle, RejectionReason rejectionReason) {
        public boolean allowed() { return rejectionReason == null; }
        static Decision allowed(String title) { return new Decision(title, null); }
        static Decision rejected(RejectionReason reason) { return new Decision(null, reason); }
    }

    public enum RejectionReason {
        MISSING, NO_LETTER_OR_DIGIT, TOO_LONG, CONTROL_CHARACTER, URL, JSON, XML_OR_HTML,
        MARKDOWN_CODE_FENCE, REPETITIVE
    }
}
