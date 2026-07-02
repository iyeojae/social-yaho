package org.huss.socialsaas.literature.dto.response;

import org.huss.socialsaas.literature.entity.LiteratureWork;

import java.time.Instant;
import java.util.List;

public record LiteratureDetailResponse(
        Long id,
        String title,
        String authorName,
        String description,
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
        LiteratureSummaryResponse summary = LiteratureSummaryResponse.from(literatureWork);
        return new LiteratureDetailResponse(
                summary.id(),
                summary.title(),
                summary.authorName(),
                literatureWork.getDescription(),
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
}


