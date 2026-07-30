package com.dj1012h.researchpilot.literature.persistence.entity;

public record LiteratureTaskPaperResultEntity(long searchTaskId, long paperId, int resultPosition, double relevanceScore) { }
