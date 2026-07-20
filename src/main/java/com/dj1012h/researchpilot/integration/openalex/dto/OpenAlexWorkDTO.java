package com.dj1012h.researchpilot.integration.openalex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAlexWorkDTO(
        String id,
        String doi,
        String title,
        @JsonProperty("publication_year")
        Integer publicationYear,
        @JsonProperty("publication_date")
        String publicationDate,
        String type,
        String language,
        @JsonProperty("cited_by_count")
        Integer citedByCount,
        List<OpenAlexAuthorshipDTO> authorships,
        @JsonProperty("primary_location")
        OpenAlexLocationDTO primaryLocation,
        @JsonProperty("best_oa_location")
        OpenAlexLocationDTO bestOpenAccessLocation,
        @JsonProperty("abstract_inverted_index")
        Map<String, List<Integer>> abstractInvertedIndex
) {

    /**
     * Compatibility constructor for callers created before language was selected.
     */
    public OpenAlexWorkDTO(
            String id,
            String doi,
            String title,
            Integer publicationYear,
            String publicationDate,
            String type,
            Integer citedByCount,
            List<OpenAlexAuthorshipDTO> authorships,
            OpenAlexLocationDTO primaryLocation,
            OpenAlexLocationDTO bestOpenAccessLocation,
            Map<String, List<Integer>> abstractInvertedIndex
    ) {
        this(
                id,
                doi,
                title,
                publicationYear,
                publicationDate,
                type,
                null,
                citedByCount,
                authorships,
                primaryLocation,
                bestOpenAccessLocation,
                abstractInvertedIndex
        );
    }
}
