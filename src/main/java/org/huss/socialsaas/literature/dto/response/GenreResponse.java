package org.huss.socialsaas.literature.dto.response;

import org.huss.socialsaas.literature.entity.Genre;

public record GenreResponse(
        Long id,
        String code,
        String name,
        String description
) {

    public static GenreResponse from(Genre genre) {
        return new GenreResponse(genre.getId(), genre.getCode(), genre.getName(), genre.getDescription());
    }
}
