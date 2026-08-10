package com.dj1012h.researchpilot.literature.rag;

import com.dj1012h.researchpilot.literature.model.PaperDTO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RagDocumentBuilderTest {

    private final RagDocumentBuilder builder = new RagDocumentBuilder();

    @Test
    void shouldBuildDeterministicNormalizedMetadataAndAbstractText() {
        PaperDTO paper = paper(
                "  Cafe\u0301\t Study  ",
                "  First\r\nabstract\tline.  ",
                List.of(
                        new PaperDTO.Author(null, " Ada\tLovelace ", null),
                        new PaperDTO.Author(null, "李  雷", null)),
                " Journal\n of Testing ",
                List.of(" remote\tsensing ", "change  detection"));

        RagPaperDocument first = builder.build(paper, "10.1000/example");
        RagPaperDocument second = builder.build(paper, "10.1000/example");

        String metadata = String.join("\n",
                "Title: Café Study",
                "Authors: Ada Lovelace, 李 雷",
                "Year: 2024",
                "Venue: Journal of Testing",
                "Keywords: remote sensing, change detection",
                "DOI: 10.1000/example");
        assertThat(first).isEqualTo(second);
        assertThat(first.segments()).hasSize(2);
        assertThat(first.segments().get(0).text()).isEqualTo(metadata);
        assertThat(first.segments().get(1).text())
                .isEqualTo(metadata + "\nAbstract: First abstract line.");
        assertThat(first.segments().get(0).contentHash()).isEqualTo(sha256(metadata));
        assertThat(first.segments().get(1).contentHash())
                .isEqualTo(sha256(metadata + "\nAbstract: First abstract line."));
    }

    @Test
    void shouldOmitBlankAbstractAndKeepShortAbstractWhole() {
        RagPaperDocument blank = builder.build(paper("Title", " \r\n\t ", List.of(), null, List.of()),
                "10.1000/example");
        RagPaperDocument shortDocument = builder.build(
                paper("Title", "A short abstract remains whole.", List.of(), null, List.of()),
                "10.1000/example");

        assertThat(blank.segments()).extracting(RagDocumentSegment::segmentType)
                .containsExactly(RagSegmentType.METADATA);
        assertThat(shortDocument.segments()).extracting(RagDocumentSegment::segmentType)
                .containsExactly(RagSegmentType.METADATA, RagSegmentType.ABSTRACT);
        assertThat(abstractText(shortDocument.segments().get(1)))
                .isEqualTo("A short abstract remains whole.");
    }

    @Test
    void shouldApplyTheFrozen350TokenWindowAnd30TokenOverlap() {
        RagPaperDocument exactBoundary = builder.build(
                paper("Title", words(350), List.of(), null, List.of()),
                "10.1000/example");
        RagPaperDocument onePastBoundary = builder.build(
                paper("Title", words(351), List.of(), null, List.of()),
                "10.1000/example");
        RagPaperDocument longDocument = builder.build(
                paper("Title", words(700), List.of(), null, List.of()),
                "10.1000/example");

        assertThat(abstractSegments(exactBoundary)).hasSize(1);
        assertThat(abstractSegments(onePastBoundary)).hasSize(2);
        assertThat(tokens(abstractText(abstractSegments(onePastBoundary).get(0))))
                .containsExactlyElementsOf(tokens(words(350)));
        assertThat(tokens(abstractText(abstractSegments(onePastBoundary).get(1))))
                .containsExactlyElementsOf(tokens(words(351)).subList(320, 351));

        List<RagDocumentSegment> chunks = abstractSegments(longDocument);
        assertThat(chunks).hasSize(3);
        assertThat(tokens(abstractText(chunks.get(0)))).hasSize(350);
        assertThat(tokens(abstractText(chunks.get(1)))).hasSize(350);
        assertThat(tokens(abstractText(chunks.get(2)))).hasSize(60);
        assertThat(tokens(abstractText(chunks.get(0))).subList(320, 350))
                .containsExactlyElementsOf(tokens(abstractText(chunks.get(1))).subList(0, 30));
        assertThat(tokens(abstractText(chunks.get(1))).subList(320, 350))
                .containsExactlyElementsOf(tokens(abstractText(chunks.get(2))).subList(0, 30));
    }

    @Test
    void shouldKeepContentHashStableAndSensitiveToExactEmbeddedText() {
        RagDocumentSegment first = builder.build(
                paper("Stable title", null, List.of(), null, List.of()),
                "10.1000/example").segments().getFirst();
        RagDocumentSegment same = builder.build(
                paper("Stable  title", null, List.of(), null, List.of()),
                "10.1000/example").segments().getFirst();
        RagDocumentSegment changed = builder.build(
                paper("Changed title", null, List.of(), null, List.of()),
                "10.1000/example").segments().getFirst();

        assertThat(first.contentHash()).isEqualTo(same.contentHash());
        assertThat(first.contentHash()).isNotEqualTo(changed.contentHash());
        assertThat(first.contentHash()).isEqualTo(sha256(first.text()));
    }

    private PaperDTO paper(
            String title,
            String abstractText,
            List<PaperDTO.Author> authors,
            String venue,
            List<String> keywords
    ) {
        return new PaperDTO(
                "W123",
                "10.1000/example",
                title,
                authors,
                2024,
                venue,
                List.of(),
                "article",
                null,
                abstractText,
                "en",
                keywords,
                3,
                PaperDTO.LiteratureSource.OPENALEX);
    }

    private List<RagDocumentSegment> abstractSegments(RagPaperDocument document) {
        return document.segments().stream()
                .filter(segment -> segment.segmentType() == RagSegmentType.ABSTRACT)
                .toList();
    }

    private String abstractText(RagDocumentSegment segment) {
        return segment.text().substring(segment.text().indexOf("\nAbstract: ") + "\nAbstract: ".length());
    }

    private String words(int count) {
        return IntStream.range(0, count).mapToObj(index -> "word" + index)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private List<String> tokens(String text) {
        return List.of(text.split(" "));
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
