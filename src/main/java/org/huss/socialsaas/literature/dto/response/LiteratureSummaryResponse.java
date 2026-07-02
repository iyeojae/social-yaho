package org.huss.socialsaas.literature.dto.response;

import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.huss.socialsaas.literature.entity.LiteratureWorkGenre;

import java.util.Comparator;
import java.util.List;

public record LiteratureSummaryResponse(
        Long id,
        String title,
        String authorName,
        String coverImageUrl,
        Long publishedYear,
        List<GenreResponse> genres
) {

    public static LiteratureSummaryResponse from(LiteratureWork literatureWork) {
        List<GenreResponse> genres = literatureWork.getGenreMappings().stream()
                .map(LiteratureWorkGenre::getGenre)
                .sorted(Comparator.comparing(genre -> genre.getCode().toLowerCase()))
                .map(GenreResponse::from)
                .toList();

        return new LiteratureSummaryResponse(
                literatureWork.getId(),
                literatureWork.getTitle(),
                literatureWork.getAuthorName(),
                literatureWork.getCoverImageUrl(),
                literatureWork.getPublishedYear(),
                genres
        );
    }
}

