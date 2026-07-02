package org.huss.socialsaas.literature;

import org.huss.socialsaas.ai.entity.BookAiTag;
import org.huss.socialsaas.ai.repository.BookAiTagRepository;
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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Autowired
    private BookAiTagRepository bookAiTagRepository;

    private Long mdStyleBookId;
    private Long xlsxBookId;

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
        work.applyImportedContent(
                "ka-test-1000",
                "https://example.com/books/ka-test-1000",
                "메밀꽃 필 무렵",
                "이효석",
                "자연과 인간의 정서를 서정적으로 그린 단편소설",
                "# 메밀꽃 필 무렵\n\n작품 전체 원문이 들어 있다고 가정합니다."
        );

        mdStyleBookId = literatureWorkRepository.saveAndFlush(work).getId();

        LiteratureWork metadataOnlyBook = LiteratureWork.create(
                "Hong Gil Dong",
                "Heo Gyun",
                "Original title: 홍길동전 | Translated language: German(Deutsch)",
                null,
                null
        );
        metadataOnlyBook.addGenre(classic);
        metadataOnlyBook.applyImportedMetadata(
                "xlsx-isbn-9783746068350",
                "Hong Gil Dong",
                "홍길동전",
                "KOREA",
                "German(Deutsch)",
                "Heo Gyun",
                null,
                null,
                null,
                null,
                null,
                "9783746068350",
                "Original title: 홍길동전 | Translated language: German(Deutsch)"
        );

        LiteratureWork savedMetadataBook = literatureWorkRepository.saveAndFlush(metadataOnlyBook);
        xlsxBookId = savedMetadataBook.getId();

        bookAiTagRepository.save(BookAiTag.create(
                savedMetadataBook,
                "이 작품은 한국 고전소설의 대표작으로, 한국 서사문학의 전통과 영웅 서사의 매력을 해외 독자에게 소개하기에 적합한 작품입니다.",
                null,
                null,
                Instant.now()
        ));
    }

    @AfterEach
    void tearDown() {
        bookAiTagRepository.deleteAllInBatch();
        literatureWorkGenreRepository.deleteAllInBatch();
        literatureWorkRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
    }

    @Test
    void getBooksAndBookDetailAndGenres() {
        List<LiteratureSummaryResponse> books = literatureService.getBooks(null, "CLASSIC");
        LiteratureDetailResponse detail = literatureService.getBook(mdStyleBookId);
        List<GenreResponse> genres = literatureService.getGenres();

        assertFalse(books.isEmpty());
        assertEquals(mdStyleBookId, detail.id());
        assertEquals("CLASSIC", books.get(0).genres().get(0).code());
        assertEquals("메밀꽃 필 무렵", detail.title());
        assertEquals("자연과 인간의 정서를 서정적으로 그린 단편소설", detail.description());
        assertEquals(detail.description(), detail.displayDescription());
        assertNull(detail.aiSummary());
        assertEquals("CONTENT_BASED_DESCRIPTION", detail.descriptionSource());
        assertEquals(1, genres.size());
        assertEquals("CLASSIC", genres.get(0).code());
    }

    @Test
    void getBookAutomaticallyEnrichesXlsxMetadataBookWithAiSummary() {
        LiteratureDetailResponse detail = literatureService.getBook(xlsxBookId);

        assertEquals(xlsxBookId, detail.id());
        assertEquals("Hong Gil Dong", detail.title());
        assertEquals("Original title: 홍길동전 | Translated language: German(Deutsch)", detail.description());
        assertEquals("이 작품은 한국 고전소설의 대표작으로, 한국 서사문학의 전통과 영웅 서사의 매력을 해외 독자에게 소개하기에 적합한 작품입니다.", detail.displayDescription());
        assertEquals(detail.displayDescription(), detail.aiSummary());
        assertEquals("AI_GENERATED_SUMMARY", detail.descriptionSource());
        assertEquals("XLSX_METADATA", detail.sourceType());
        assertFalse(detail.contentAvailable());
    }
}



