package org.huss.socialsaas.literature.dto.response;

import org.huss.socialsaas.literature.entity.LiteratureWork;

import java.time.Instant;
import java.util.List;

public record LiteratureDetailResponse(
        Long id,
        String title,
        String authorName,
        String description,
        String displayDescription,
        String aiSummary,
        String descriptionSource,
        String originalTitle,
        String country,
        String translatedLanguage,
        String authorNameKorean,
        String translator,
        String translationPublicationSupport,
        String publisher,
        String isbn,
        String sourceBookId,
        boolean contentAvailable,
        String sourceType,
        String coverImageUrl,
        Long publishedYear,
        boolean active,
        List<GenreResponse> genres,
        Instant createdAt,
        Instant updatedAt
) {

    public static LiteratureDetailResponse from(LiteratureWork literatureWork) {
        return from(literatureWork, literatureWork.getDescription(), null, resolveDescriptionSource(literatureWork, null));
    }

    public static LiteratureDetailResponse from(
            LiteratureWork literatureWork,
            String displayDescription,
            String aiSummary,
            String descriptionSource
    ) {
        LiteratureSummaryResponse summary = LiteratureSummaryResponse.from(literatureWork);
        return new LiteratureDetailResponse(
                summary.id(),
                summary.title(),
                summary.authorName(),
                literatureWork.getDescription(),
                displayDescription,
                aiSummary,
                descriptionSource,
                literatureWork.getOriginalTitle(),
                literatureWork.getCountry(),
                literatureWork.getTranslatedLanguage(),
                literatureWork.getAuthorNameKorean(),
                literatureWork.getTranslator(),
                literatureWork.getTranslationPublicationSupport(),
                literatureWork.getPublisher(),
                literatureWork.getIsbn(),
                literatureWork.getSourceBookId(),
                literatureWork.isContentAvailable(),
                literatureWork.getSourceType(),
                summary.coverImageUrl(),
                summary.publishedYear(),
                literatureWork.isActive(),
                summary.genres(),
                literatureWork.getCreatedAt(),
                literatureWork.getUpdatedAt()
        );
    }

    private static String resolveDescriptionSource(LiteratureWork literatureWork, String aiSummary) {
        if (aiSummary != null && !aiSummary.isBlank()) {
            return "AI_GENERATED_SUMMARY";
        }
        if (literatureWork.isContentAvailable()) {
            return "CONTENT_BASED_DESCRIPTION";
        }
        return "IMPORTED_METADATA_DESCRIPTION";
    }
}



