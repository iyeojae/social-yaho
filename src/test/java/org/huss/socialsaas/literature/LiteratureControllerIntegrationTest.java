package org.huss.socialsaas.literature;

import org.huss.socialsaas.literature.dto.response.GenreResponse;
import org.huss.socialsaas.literature.dto.response.LiteratureDetailResponse;
import org.huss.socialsaas.literature.dto.response.LiteratureSummaryResponse;
import org.huss.socialsaas.literature.entity.Genre;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.huss.socialsaas.literature.repository.GenreRepository;
import org.huss.socialsaas.literature.repository.LiteratureWorkGenreRepository;
import org.huss.socialsaas.literature.repository.LiteratureWorkRepository;
import org.huss.socialsaas.literature.service.LiteratureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("test")
class LiteratureControllerIntegrationTest {

    @Autowired
    private LiteratureService literatureService;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private LiteratureWorkRepository literatureWorkRepository;

    @Autowired
    private LiteratureWorkGenreRepository literatureWorkGenreRepository;

    private Long savedBookId;

    @BeforeEach
    void setUp() {
        Genre classic = genreRepository.save(Genre.builder()
                .code("CLASSIC")
                .name("고전문학")
                .description("한국 고전 및 근대 문학")
                .build());

        LiteratureWork work = LiteratureWork.create(
                "메밀꽃 필 무렵",
                "이효석",
                "자연과 인간의 정서를 서정적으로 그린 단편소설",
                "https://example.com/books/1.jpg",
                1936L
        );
        work.addGenre(classic);

        savedBookId = literatureWorkRepository.saveAndFlush(work).getId();
    }

    @AfterEach
    void tearDown() {
        literatureWorkGenreRepository.deleteAllInBatch();
        literatureWorkRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
    }

    @Test
    void getBooksAndBookDetailAndGenres() {
        List<LiteratureSummaryResponse> books = literatureService.getBooks(null, "CLASSIC");
        LiteratureDetailResponse detail = literatureService.getBook(savedBookId);
        List<GenreResponse> genres = literatureService.getGenres();

        assertFalse(books.isEmpty());
        assertEquals(savedBookId, books.get(0).id());
        assertEquals("CLASSIC", books.get(0).genres().get(0).code());
        assertEquals(savedBookId, detail.id());
        assertEquals("메밀꽃 필 무렵", detail.title());
        assertEquals(1, genres.size());
        assertEquals("CLASSIC", genres.get(0).code());
    }
}


