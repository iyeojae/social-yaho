package org.huss.socialsaas.literature.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.huss.socialsaas.global.exception.BusinessException;
import org.huss.socialsaas.global.exception.ErrorCode;
import org.huss.socialsaas.literature.dto.request.XlsxBookImportRequest;
import org.huss.socialsaas.literature.dto.response.XlsxBookImportResponse;
import org.huss.socialsaas.literature.entity.Genre;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.huss.socialsaas.literature.repository.GenreRepository;
import org.huss.socialsaas.literature.repository.LiteratureWorkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class XlsxBookImportService {

    private static final String FALLBACK_CLASSIFICATION = "UNCLASSIFIED";
    private static final int IMPORT_BATCH_SIZE = 200;
    private static final int RESPONSE_BOOK_LIMIT = 100;
    private static final int LEGACY_SAFE_GENRE_NAME_LENGTH = 100;
    private static final DataFormatter DATA_FORMATTER = new DataFormatter();
    private static final Set<String> REQUIRED_HEADERS = Set.of("title", "author", "classification");

    private final LiteratureWorkRepository literatureWorkRepository;
    private final GenreRepository genreRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.book-import.xlsx-path:TRANSLATEDBOOKS_20260702032206.xlsx}")
    private String defaultImportPath;

    public XlsxBookImportResponse importBooks(XlsxBookImportRequest request) {
        XlsxBookImportRequest effectiveRequest = request == null ? XlsxBookImportRequest.empty() : request;
        String importPath = firstNonBlank(effectiveRequest.importPath(), defaultImportPath);

        Path xlsxPath = Path.of(importPath).toAbsolutePath().normalize();
        if (!Files.exists(xlsxPath) || Files.isDirectory(xlsxPath)) {
            throw new BusinessException(ErrorCode.BOOK_IMPORT_XLSX_PATH_NOT_FOUND);
        }

        try (InputStream inputStream = Files.newInputStream(xlsxPath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = resolveSheet(workbook, effectiveRequest.sheetName());
            Map<String, Integer> headerMap = extractHeaderMap(sheet);
            validateHeaders(headerMap);

            List<XlsxBookImportResponse.ImportedBook> importedBooks = new ArrayList<>();
            int totalRows = 0;
            int importedRows = 0;
            int createdCount = 0;
            int updatedCount = 0;

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                totalRows++;

                ParsedXlsxBook parsedBook = parseRow(row, headerMap);
                if (parsedBook.title() == null || parsedBook.title().isBlank()) {
                    continue;
                }

                importedRows++;
                Optional<LiteratureWork> existing = literatureWorkRepository.findBySourceBookId(parsedBook.sourceBookId());

                LiteratureWork work;
                String status;
                if (existing.isPresent()) {
                    work = existing.get();
                    status = "UPDATED";
                    updatedCount++;
                } else {
                    work = LiteratureWork.create(
                            parsedBook.title(),
                            parsedBook.authorName(),
                            parsedBook.description(),
                            null,
                            parsedBook.publishedYear()
                    );
                    status = "CREATED";
                    createdCount++;
                }

                work.applyImportedMetadata(
                        parsedBook.sourceBookId(),
                        parsedBook.title(),
                        parsedBook.originalTitle(),
                        parsedBook.country(),
                        parsedBook.translatedLanguage(),
                        parsedBook.authorName(),
                        parsedBook.authorNameKorean(),
                        parsedBook.translator(),
                        parsedBook.translationPublicationSupport(),
                        parsedBook.publisher(),
                        parsedBook.publishedYear(),
                        parsedBook.isbn(),
                        parsedBook.description()
                );

                List<Genre> resolvedGenres = resolveGenres(parsedBook.classification());
                resolvedGenres.forEach(work::addGenre);

                LiteratureWork savedWork = literatureWorkRepository.save(work);
                if (importedBooks.size() < RESPONSE_BOOK_LIMIT) {
                    importedBooks.add(new XlsxBookImportResponse.ImportedBook(
                            savedWork.getId(),
                            parsedBook.sourceBookId(),
                            parsedBook.title(),
                            parsedBook.translatedLanguage(),
                            resolvedGenres.stream().map(Genre::getCode).toList(),
                            status
                    ));
                }

                if (importedRows % IMPORT_BATCH_SIZE == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }

            entityManager.flush();
            entityManager.clear();

            return new XlsxBookImportResponse(
                    xlsxPath.toString(),
                    sheet.getSheetName(),
                    totalRows,
                    importedRows,
                    createdCount,
                    updatedCount,
                    Instant.now(),
                    importedBooks
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BOOK_IMPORT_XLSX_FAILED);
        }
    }

    private Sheet resolveSheet(Workbook workbook, String requestedSheetName) {
        String normalizedSheetName = normalizeHeader(requestedSheetName);
        if (normalizedSheetName == null) {
            return workbook.getSheetAt(0);
        }

        Sheet sheet = workbook.getSheet(requestedSheetName);
        if (sheet == null) {
            throw new BusinessException(ErrorCode.BOOK_IMPORT_XLSX_INVALID_HEADER);
        }
        return sheet;
    }

    private Map<String, Integer> extractHeaderMap(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new BusinessException(ErrorCode.BOOK_IMPORT_XLSX_INVALID_HEADER);
        }

        Map<String, Integer> headerMap = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = normalizeHeader(DATA_FORMATTER.formatCellValue(cell));
            if (header != null && !header.isBlank()) {
                headerMap.put(header, cell.getColumnIndex());
            }
        }
        return headerMap;
    }

    private void validateHeaders(Map<String, Integer> headerMap) {
        boolean valid = REQUIRED_HEADERS.stream().allMatch(headerMap::containsKey);
        if (!valid) {
            throw new BusinessException(ErrorCode.BOOK_IMPORT_XLSX_INVALID_HEADER);
        }
    }

    private ParsedXlsxBook parseRow(Row row, Map<String, Integer> headerMap) {
        String title = readCell(row, headerMap, "title");
        String originalTitle = readCell(row, headerMap, "original title");
        String country = readCell(row, headerMap, "country");
        String translatedLanguage = readCell(row, headerMap, "translated language");
        String authorName = firstNonBlank(readCell(row, headerMap, "author"), "Unknown");
        String authorNameKorean = readCell(row, headerMap, "author name(korean)");
        String translator = readCell(row, headerMap, "translator");
        String translationPublicationSupport = readCell(row, headerMap, "translation and publication support");
        String publisher = readCell(row, headerMap, "publisher");
        Long publishedYear = parsePublishedYear(readCell(row, headerMap, "published year"));
        String classification = firstNonBlank(readCell(row, headerMap, "classification"), FALLBACK_CLASSIFICATION);
        String isbn = readCell(row, headerMap, "isbn");
        String description = buildDescription(originalTitle, translatedLanguage, publisher, classification, country);
        String sourceBookId = buildSourceBookId(title, authorName, translatedLanguage, isbn);

        return new ParsedXlsxBook(
                sourceBookId,
                title,
                originalTitle,
                country,
                translatedLanguage,
                authorName,
                authorNameKorean,
                translator,
                translationPublicationSupport,
                publisher,
                publishedYear,
                classification,
                isbn,
                description
        );
    }

    private String readCell(Row row, Map<String, Integer> headerMap, String headerKey) {
        Integer columnIndex = headerMap.get(headerKey);
        if (columnIndex == null) {
            return null;
        }
        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }
        return sanitizeValue(DATA_FORMATTER.formatCellValue(cell));
    }

    private boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            String value = cell == null ? null : sanitizeValue(DATA_FORMATTER.formatCellValue(cell));
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private Long parsePublishedYear(String value) {
        String normalized = sanitizeValue(value);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        String digits = normalized.replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return null;
        }
        try {
            return Long.parseLong(digits.substring(0, 4));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<Genre> resolveGenres(String classification) {
        String normalizedClassification = firstNonBlank(classification, FALLBACK_CLASSIFICATION);
        List<String> tokens = splitClassification(normalizedClassification);
        List<Genre> genres = new ArrayList<>();
        for (String token : tokens) {
            String normalizedName = sanitizeValue(token);
            if (normalizedName == null || normalizedName.isBlank()) {
                continue;
            }
            String normalizedCode = toGenreCode(normalizedName);
            String displayName = toGenreDisplayName(normalizedName);
            Genre genre = genreRepository.findByCodeIgnoreCase(normalizedCode)
                    .orElseGet(() -> genreRepository.save(Genre.builder()
                            .code(normalizedCode)
                            .name(displayName)
                            .description("엑셀 메타데이터에서 import된 분류: " + normalizedName)
                            .build()));
            genres.add(genre);
        }
        return genres.isEmpty() ? resolveGenres(FALLBACK_CLASSIFICATION) : genres;
    }

    private List<String> splitClassification(String classification) {
        String normalized = classification.replace("/", ",")
                .replace("|", ",")
                .replace(";", ",");
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split(",")) {
            String cleaned = sanitizeValue(token);
            if (!cleaned.isBlank()) {
                tokens.add(cleaned);
            }
        }
        return tokens.isEmpty() ? List.of(FALLBACK_CLASSIFICATION) : List.copyOf(tokens);
    }

    private String buildDescription(String originalTitle, String translatedLanguage, String publisher, String classification, String country) {
        List<String> fragments = new ArrayList<>();
        if (originalTitle != null && !originalTitle.isBlank()) {
            fragments.add("Original title: " + originalTitle);
        }
        if (translatedLanguage != null && !translatedLanguage.isBlank()) {
            fragments.add("Translated language: " + translatedLanguage);
        }
        if (classification != null && !classification.isBlank()) {
            fragments.add("Classification: " + classification);
        }
        if (country != null && !country.isBlank()) {
            fragments.add("Country: " + country);
        }
        if (publisher != null && !publisher.isBlank()) {
            fragments.add("Publisher: " + publisher);
        }
        return fragments.isEmpty() ? "엑셀 메타데이터로 import된 도서입니다." : String.join(" | ", fragments);
    }

    private String buildSourceBookId(String title, String authorName, String translatedLanguage, String isbn) {
        String normalizedIsbn = sanitizeValue(isbn);
        if (normalizedIsbn != null && !normalizedIsbn.isBlank()) {
            String compact = normalizedIsbn.replaceAll("[^A-Za-z0-9]", "");
            return truncate("xlsx-isbn-" + compact, 50);
        }

        String seed = String.join("|",
                firstNonBlank(title, "untitled"),
                firstNonBlank(authorName, "unknown"),
                firstNonBlank(translatedLanguage, "unknown")
        );
        return "xlsx-" + sha256(seed).substring(0, 32);
    }

    private String toGenreCode(String genreName) {
        String normalized = sanitizeValue(genreName).toUpperCase(Locale.ROOT);
        normalized = normalized.replace('&', ' ');
        normalized = normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}가-힣]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            return FALLBACK_CLASSIFICATION;
        }
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 96) + "_" + sha256(normalized).substring(0, 23);
    }

    private String toGenreDisplayName(String genreName) {
        String normalized = sanitizeValue(genreName);
        if (normalized == null || normalized.isBlank()) {
            return FALLBACK_CLASSIFICATION;
        }
        if (normalized.length() <= LEGACY_SAFE_GENRE_NAME_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, LEGACY_SAFE_GENRE_NAME_LENGTH - 3).trim() + "...";
    }

    private String sha256(String seed) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(seed.getBytes());
            StringBuilder builder = new StringBuilder();
            for (byte current : digest) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String firstNonBlank(String primary, String fallback) {
        String normalizedPrimary = sanitizeValue(primary);
        if (normalizedPrimary != null && !normalizedPrimary.isBlank()) {
            return normalizedPrimary;
        }
        return sanitizeValue(fallback);
    }

    private String sanitizeValue(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replace('\u00A0', ' ')
                .trim();
    }

    private String normalizeHeader(String header) {
        String normalized = sanitizeValue(header);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private record ParsedXlsxBook(
            String sourceBookId,
            String title,
            String originalTitle,
            String country,
            String translatedLanguage,
            String authorName,
            String authorNameKorean,
            String translator,
            String translationPublicationSupport,
            String publisher,
            Long publishedYear,
            String classification,
            String isbn,
            String description
    ) {
    }
}





