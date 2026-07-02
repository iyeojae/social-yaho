package org.huss.socialsaas.literature;

import org.huss.socialsaas.literature.dto.request.MdBookImportRequest;
import org.huss.socialsaas.literature.dto.response.MdBookImportResponse;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.huss.socialsaas.literature.repository.GenreRepository;
import org.huss.socialsaas.literature.repository.LiteratureWorkGenreRepository;
import org.huss.socialsaas.literature.repository.LiteratureWorkRepository;
import org.huss.socialsaas.literature.service.MdBookImportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class MdBookImportIntegrationTest {

    @Autowired
    private MdBookImportService mdBookImportService;

    @Autowired
    private LiteratureWorkRepository literatureWorkRepository;

    @Autowired
    private LiteratureWorkGenreRepository literatureWorkGenreRepository;

    @Autowired
    private GenreRepository genreRepository;

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        literatureWorkGenreRepository.deleteAllInBatch();
        literatureWorkRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
    }

    @Test
    void importMarkdownBooksCreatesAndUpdatesWorks() throws IOException {
        Path markdownFile = tempDir.resolve("ka9999_Test_Book.md");
        Files.writeString(markdownFile, """
                # Test Book
                
                **저자:** Test Author
                **출처:** https://example.com/books/ka9999
                **책 ID:** ka9999
                **장르:** 고전소설, 민담
                
                ## Intro
                
                This is the very first paragraph of the imported markdown book. It should become the description.
                
                ## Chapter 1
                
                Full markdown content should be stored as-is.
                """);

        MdBookImportResponse firstImport = mdBookImportService.importBooks(
                new MdBookImportRequest(tempDir.toString())
        );

        assertEquals(1, firstImport.totalFiles());
        assertEquals(1, firstImport.createdCount());
        assertEquals(0, firstImport.updatedCount());

        LiteratureWork saved = literatureWorkRepository.findBySourceBookId("ka9999").orElseThrow();
        LiteratureWork loadedWithGenres = literatureWorkRepository.findDetailById(saved.getId()).orElseThrow();
        assertEquals("Test Book", saved.getTitle());
        assertEquals("Test Author", saved.getAuthorName());
        assertEquals("https://example.com/books/ka9999", saved.getSourceUrl());
        assertNotNull(saved.getContentMarkdown());
        assertTrue(saved.getContentMarkdown().contains("Full markdown content should be stored as-is."));
        assertEquals(2, loadedWithGenres.getGenreMappings().size());
        assertEquals(2, firstImport.books().get(0).genreCodes().size());
        assertTrue(firstImport.books().get(0).genreCodes().contains("고전소설"));
        assertTrue(firstImport.books().get(0).genreCodes().contains("민담"));

        MdBookImportResponse secondImport = mdBookImportService.importBooks(
                new MdBookImportRequest(tempDir.toString())
        );

        assertEquals(0, secondImport.createdCount());
        assertEquals(1, secondImport.updatedCount());
        assertEquals(1, literatureWorkRepository.count());
    }
}




