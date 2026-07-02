package org.huss.socialsaas.literature;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.huss.socialsaas.literature.dto.request.XlsxBookImportRequest;
import org.huss.socialsaas.literature.dto.response.XlsxBookImportResponse;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.huss.socialsaas.literature.repository.GenreRepository;
import org.huss.socialsaas.literature.repository.LiteratureWorkGenreRepository;
import org.huss.socialsaas.literature.repository.LiteratureWorkRepository;
import org.huss.socialsaas.literature.service.XlsxBookImportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class XlsxBookImportIntegrationTest {

    @Autowired
    private XlsxBookImportService xlsxBookImportService;

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
    void importXlsxMetadataBooksCreatesAndUpdatesWorks() throws IOException {
        String longAuthorName = "Kim Man-jung ".repeat(20).trim();
        Path xlsxFile = tempDir.resolve("translated_books.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream outputStream = Files.newOutputStream(xlsxFile)) {
            var sheet = workbook.createSheet("Books");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Title");
            header.createCell(1).setCellValue("Original Title");
            header.createCell(2).setCellValue("Country");
            header.createCell(3).setCellValue("Translated Language");
            header.createCell(4).setCellValue("Author");
            header.createCell(5).setCellValue("Author Name(Korean)");
            header.createCell(6).setCellValue("Translator");
            header.createCell(7).setCellValue("Translation and Publication Support");
            header.createCell(8).setCellValue("Publisher");
            header.createCell(9).setCellValue("Published Year");
            header.createCell(10).setCellValue("Classification");
            header.createCell(11).setCellValue("ISBN");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("The Cloud Dream of the Nine");
            row.createCell(1).setCellValue("구운몽");
            row.createCell(2).setCellValue("Korea");
            row.createCell(3).setCellValue("English");
            row.createCell(4).setCellValue(longAuthorName);
            row.createCell(5).setCellValue("김만중");
            row.createCell(6).setCellValue("James S. Gale");
            row.createCell(7).setCellValue("LTI Korea");
            row.createCell(8).setCellValue("Example Publisher");
            row.createCell(9).setCellValue("2023");
            row.createCell(10).setCellValue("Classic Novel, Fiction, Anthology of translated Korean literature for international archival discovery and multilingual preservation");
            row.createCell(11).setCellValue("9781234567890");

            workbook.write(outputStream);
        }

        XlsxBookImportResponse firstImport = xlsxBookImportService.importBooks(
                new XlsxBookImportRequest(xlsxFile.toString(), "Books")
        );

        assertEquals(1, firstImport.totalRows());
        assertEquals(1, firstImport.importedRows());
        assertEquals(1, firstImport.createdCount());
        assertEquals(0, firstImport.updatedCount());
        assertEquals("English", firstImport.books().get(0).translatedLanguage());
        assertTrue(firstImport.books().get(0).genreCodes().contains("CLASSIC_NOVEL"));
        assertTrue(firstImport.books().get(0).genreCodes().contains("FICTION"));
        assertEquals(3, firstImport.books().get(0).genreCodes().size());
        assertTrue(firstImport.books().get(0).genreCodes().stream().allMatch(code -> code.length() <= 120));
        assertTrue(genreRepository.findAll().stream().allMatch(genre -> genre.getName().length() <= 100));

        LiteratureWork saved = literatureWorkRepository.findBySourceBookId("xlsx-isbn-9781234567890").orElseThrow();
        LiteratureWork loadedWithGenres = literatureWorkRepository.findDetailById(saved.getId()).orElseThrow();
        assertEquals("The Cloud Dream of the Nine", saved.getTitle());
        assertEquals("구운몽", saved.getOriginalTitle());
        assertEquals("Korea", saved.getCountry());
        assertEquals("English", saved.getTranslatedLanguage());
        assertEquals(150, saved.getAuthorName().length());
        assertEquals("김만중", saved.getAuthorNameKorean());
        assertEquals("James S. Gale", saved.getTranslator());
        assertEquals("LTI Korea", saved.getTranslationPublicationSupport());
        assertEquals("Example Publisher", saved.getPublisher());
        assertEquals(2023L, saved.getPublishedYear());
        assertEquals("9781234567890", saved.getIsbn());
        assertEquals("XLSX_METADATA", saved.getSourceType());
        assertFalse(saved.isContentAvailable());
        assertEquals(3, loadedWithGenres.getGenreMappings().size());

        XlsxBookImportResponse secondImport = xlsxBookImportService.importBooks(
                new XlsxBookImportRequest(xlsxFile.toString(), "Books")
        );

        assertEquals(0, secondImport.createdCount());
        assertEquals(1, secondImport.updatedCount());
        assertEquals(1, literatureWorkRepository.count());
    }
}




