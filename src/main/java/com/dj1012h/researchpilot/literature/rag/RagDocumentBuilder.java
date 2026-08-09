package com.dj1012h.researchpilot.literature.rag;

import com.dj1012h.researchpilot.literature.model.PaperDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds the frozen t1/c350/o30/n1 controlled paper text deterministically. */
public class RagDocumentBuilder {

    public static final int ABSTRACT_TOKEN_LIMIT = 350;
    public static final int ABSTRACT_TOKEN_OVERLAP = 30;

    public RagPaperDocument build(PaperDTO paper, String normalizedDoi) {
        Objects.requireNonNull(paper, "paper must not be null");
        String doi = normalizeRequired(normalizedDoi, "normalizedDoi");
        String title = normalizeRequired(paper.title(), "title");
        List<String> authors = paper.authors().stream()
                .map(PaperDTO.Author::displayName)
                .map(value -> normalizeRequired(value, "author displayName"))
                .toList();
        String venue = normalizeOptional(paper.venue());
        String language = normalizeOptional(paper.language());
        List<String> keywords = paper.keywords().stream()
                .map(this::normalizeOptional)
                .filter(value -> !value.isEmpty())
                .toList();

        String metadata = String.join("\n",
                "Title: " + title,
                "Authors: " + String.join(", ", authors),
                "Year: " + (paper.publicationYear() == null ? "" : paper.publicationYear()),
                "Venue: " + venue,
                "Keywords: " + String.join(", ", keywords),
                "DOI: " + doi);

        List<RagDocumentSegment> segments = new ArrayList<>();
        segments.add(segment(RagSegmentType.METADATA, 0, metadata));
        String normalizedAbstract = normalizeOptional(paper.abstractText());
        List<String> abstractChunks = chunks(normalizedAbstract);
        for (int index = 0; index < abstractChunks.size(); index++) {
            segments.add(segment(
                    RagSegmentType.ABSTRACT,
                    index,
                    metadata + "\nAbstract: " + abstractChunks.get(index)));
        }
        return new RagPaperDocument(
                doi,
                title,
                authors,
                paper.publicationYear(),
                venue,
                language,
                keywords,
                segments);
    }

    private RagDocumentSegment segment(RagSegmentType type, int index, String text) {
        return new RagDocumentSegment(type, index, text, sha256(text));
    }

    private List<String> chunks(String text) {
        if (text.isEmpty()) {
            return List.of();
        }
        List<TokenSpan> tokens = tokenize(text);
        if (tokens.size() <= ABSTRACT_TOKEN_LIMIT) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        int startToken = 0;
        while (startToken < tokens.size()) {
            int endToken = Math.min(startToken + ABSTRACT_TOKEN_LIMIT, tokens.size());
            TokenSpan first = tokens.get(startToken);
            TokenSpan last = tokens.get(endToken - 1);
            chunks.add(text.substring(first.start(), last.end()));
            if (endToken == tokens.size()) {
                break;
            }
            startToken = endToken - ABSTRACT_TOKEN_OVERLAP;
        }
        return List.copyOf(chunks);
    }

    private List<TokenSpan> tokenize(String text) {
        List<TokenSpan> tokens = new ArrayList<>();
        int offset = 0;
        while (offset < text.length()) {
            int codePoint = text.codePointAt(offset);
            int width = Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                offset += width;
                continue;
            }
            if (isCjk(codePoint)) {
                tokens.add(new TokenSpan(offset, offset + width));
                offset += width;
                continue;
            }
            if (isWord(codePoint)) {
                int start = offset;
                offset += width;
                while (offset < text.length()) {
                    int next = text.codePointAt(offset);
                    if (isCjk(next) || !isWord(next)) {
                        break;
                    }
                    offset += Character.charCount(next);
                }
                tokens.add(new TokenSpan(start, offset));
                continue;
            }
            tokens.add(new TokenSpan(offset, offset + width));
            offset += width;
        }
        return tokens;
    }

    private boolean isWord(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isLetterOrDigit(codePoint)
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }

    private boolean isCjk(int codePoint) {
        return switch (Character.UnicodeScript.of(codePoint)) {
            case HAN, HANGUL, HIRAGANA, KATAKANA, BOPOMOFO -> true;
            default -> false;
        };
    }

    private String normalizeRequired(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank after normalization");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String nfc = Normalizer.normalize(value, Normalizer.Form.NFC);
        StringBuilder result = new StringBuilder(nfc.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < nfc.length();) {
            int codePoint = nfc.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isWhitespace(codePoint)) {
                pendingSpace = result.length() > 0;
                continue;
            }
            if (Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException("controlled text contains a non-whitespace control character");
            }
            if (pendingSpace) {
                result.append(' ');
                pendingSpace = false;
            }
            result.appendCodePoint(codePoint);
        }
        return result.toString();
    }

    private boolean isWhitespace(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isWhitespace(codePoint)
                || type == Character.SPACE_SEPARATOR
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record TokenSpan(int start, int end) { }
}
