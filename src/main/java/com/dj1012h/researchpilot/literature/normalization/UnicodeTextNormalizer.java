package com.dj1012h.researchpilot.literature.normalization;

import java.text.Normalizer;
import java.util.Locale;

/** Shared conservative Unicode and punctuation normalization for bibliographic fields. */
final class UnicodeTextNormalizer {

    private UnicodeTextNormalizer() {
    }

    static String normalize(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String value = Normalizer.normalize(rawValue, Normalizer.Form.NFKC);
        StringBuilder normalized = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                if (!normalized.isEmpty()) {
                    pendingSpace = true;
                }
                continue;
            }

            if (pendingSpace) {
                normalized.append(' ');
                pendingSpace = false;
            }
            if (codePoint == '\u2026') {
                normalized.append("...");
            } else {
                normalized.appendCodePoint(canonicalPunctuation(codePoint));
            }
        }

        String result = normalized.toString().toLowerCase(Locale.ROOT).trim();
        if (result.isEmpty() || result.codePoints().noneMatch(Character::isLetterOrDigit)) {
            return null;
        }
        return result;
    }

    private static int canonicalPunctuation(int codePoint) {
        return switch (codePoint) {
            case '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2015',
                    '\u2212', '\uFE58', '\uFE63', '\uFF0D' -> '-';
            case '\u2018', '\u2019', '\u201A', '\u201B', '\u275B', '\u275C' -> '\'';
            case '\u201C', '\u201D', '\u201E', '\u201F', '\u275D', '\u275E' -> '"';
            default -> codePoint;
        };
    }
}
