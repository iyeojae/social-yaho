package org.huss.socialsaas.literature.service;

import lombok.RequiredArgsConstructor;
import org.huss.socialsaas.global.exception.BusinessException;
import org.huss.socialsaas.global.exception.ErrorCode;
import org.huss.socialsaas.literature.dto.request.MdBookImportRequest;
import org.huss.socialsaas.literature.dto.response.MdBookImportResponse;
import org.huss.socialsaas.literature.entity.Genre;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.huss.socialsaas.literature.repository.GenreRepository;
import org.huss.socialsaas.literature.repository.LiteratureWorkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional
public class MdBookImportService {

    private static final int DESCRIPTION_MAX_LENGTH = 320;
    private static final int LEGACY_SAFE_GENRE_NAME_LENGTH = 100;
    private static final List<String> DESCRIPTION_SKIP_PHRASES = List.of(
            "The Digital Library of Korean Classics is a project undertaken",
            "LTI Korea is an affiliate of the Ministry of Culture",
            "This e-book was made by scanning and converting the original book",
            "All rights reserved. All texts thus made available are for personal use only"
    );

    private final LiteratureWorkRepository literatureWorkRepository;
    private final GenreRepository genreRepository;

    @Value("${app.book-import.path:md_books}")
    private String defaultImportPath;

    public MdBookImportResponse importBooks(MdBookImportRequest request) {
        MdBookImportRequest effectiveRequest = request == null ? MdBookImportRequest.empty() : request;
        String importPath = firstNonBlank(effectiveRequest.importPath(), defaultImportPath);

        Path directory = Path.of(importPath).toAbsolutePath().normalize();
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            throw new BusinessException(ErrorCode.BOOK_IMPORT_PATH_NOT_FOUND);
        }

        List<MdBookImportResponse.ImportedBook> importedBooks = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;

        try (Stream<Path> pathStream = Files.list(directory)) {
            List<Path> markdownFiles = pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();

            for (Path markdownFile : markdownFiles) {
                ParsedMdBook parsedBook = parseMarkdownFile(markdownFile);
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
                            null
                    );
                    status = "CREATED";
                    createdCount++;
                }

                work.applyImportedContent(
                        parsedBook.sourceBookId(),
                        parsedBook.sourceUrl(),
                        parsedBook.title(),
                        parsedBook.authorName(),
                        parsedBook.description(),
                        parsedBook.contentMarkdown()
                );
                List<Genre> resolvedGenres = resolveGenres(parsedBook.genreValues());
                resolvedGenres.forEach(work::addGenre);

                LiteratureWork savedWork = literatureWorkRepository.save(work);
                importedBooks.add(new MdBookImportResponse.ImportedBook(
                        savedWork.getId(),
                        parsedBook.sourceBookId(),
                        parsedBook.title(),
                        resolvedGenres.stream().map(Genre::getCode).toList(),
                        status
                ));
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BOOK_IMPORT_FAILED);
        }

        return new MdBookImportResponse(
                directory.toString(),
                importedBooks.size(),
                createdCount,
                updatedCount,
                Instant.now(),
                importedBooks
        );
    }

    private List<Genre> resolveGenres(List<String> rawGenres) {
        if (rawGenres == null || rawGenres.isEmpty()) {
            throw new BusinessException(ErrorCode.BOOK_IMPORT_GENRE_MISSING);
        }

        List<Genre> genres = new ArrayList<>();
        for (String rawGenre : rawGenres) {
            String normalizedName = sanitizeValue(rawGenre);
            if (normalizedName == null || normalizedName.isBlank()) {
                continue;
            }

            String normalizedCode = toGenreCode(normalizedName);
            String displayName = toGenreDisplayName(normalizedName);
            Genre genre = genreRepository.findByCodeIgnoreCase(normalizedCode)
                    .orElseGet(() -> genreRepository.save(Genre.builder()
                            .code(normalizedCode)
                            .name(displayName)
                            .description("마크다운 메타데이터에서 import된 장르: " + normalizedName)
                            .build()));
            genres.add(genre);
        }

        if (genres.isEmpty()) {
            throw new BusinessException(ErrorCode.BOOK_IMPORT_GENRE_MISSING);
        }

        return genres;
    }

    private ParsedMdBook parseMarkdownFile(Path markdownFile) throws IOException {
        String contentMarkdown = Files.readString(markdownFile, StandardCharsets.UTF_8);
        List<String> lines = Files.readAllLines(markdownFile, StandardCharsets.UTF_8);

        String sourceBookId = firstNonBlank(
                extractMetadataValue(lines, "**책 ID:**"),
                deriveSourceBookId(markdownFile)
        );
        String title = firstNonBlank(
                extractHeading(lines),
                deriveTitleFromFilename(markdownFile)
        );
        String authorName = firstNonBlank(
                extractMetadataValue(lines, "**저자:**"),
                "Unknown"
        );
        String sourceUrl = extractMetadataValue(lines, "**출처:**");
        List<String> genreValues = extractGenreValues(lines);
        String description = buildDescription(lines, title);

        return new ParsedMdBook(
                sourceBookId,
                title,
                authorName,
                sourceUrl,
                genreValues,
                description,
                contentMarkdown
        );
    }

    private List<String> extractGenreValues(List<String> lines) {
        String metadata = firstNonBlank(
                extractMetadataValue(lines, "**장르:**"),
                extractMetadataValue(lines, "**책 장르:**")
        );
        if (metadata == null || metadata.isBlank()) {
            return List.of();
        }

        String normalized = metadata.replace("/", ",")
                .replace("|", ",")
                .replace(";", ",");

        Set<String> deduplicated = new LinkedHashSet<>();
        for (String token : normalized.split(",")) {
            String cleaned = sanitizeValue(token);
            if (cleaned != null && !cleaned.isBlank()) {
                deduplicated.add(cleaned);
            }
        }
        return List.copyOf(deduplicated);
    }

    private String extractHeading(List<String> lines) {
        return lines.stream()
                .map(this::sanitizeValue)
                .filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2).trim())
                .findFirst()
                .orElse(null);
    }

    private String extractMetadataValue(List<String> lines, String prefix) {
        return lines.stream()
                .map(this::sanitizeValue)
                .filter(line -> line.startsWith(prefix))
                .map(line -> sanitizeValue(line.substring(prefix.length())))
                .findFirst()
                .orElse(null);
    }

    private String deriveSourceBookId(Path markdownFile) {
        String fileName = stripExtension(markdownFile.getFileName().toString());
        int separatorIndex = fileName.indexOf('_');
        if (separatorIndex > 0) {
            return sanitizeValue(fileName.substring(0, separatorIndex));
        }
        return sanitizeValue(fileName);
    }

    private String deriveTitleFromFilename(Path markdownFile) {
        String fileName = stripExtension(markdownFile.getFileName().toString());
        int separatorIndex = fileName.indexOf('_');
        String rawTitle = separatorIndex > -1 ? fileName.substring(separatorIndex + 1) : fileName;
        return sanitizeValue(rawTitle.replace('_', ' '));
    }

    private String buildDescription(List<String> lines, String title) {
        StringBuilder paragraph = new StringBuilder();
        boolean inContentsSection = false;

        for (String rawLine : lines) {
            String line = sanitizeValue(rawLine);

            if (line.equalsIgnoreCase("CONTENTS") || line.equals("目录")) {
                inContentsSection = true;
                paragraph.setLength(0);
                continue;
            }
            if (inContentsSection) {
                if (line.equals("---")) {
                    inContentsSection = false;
                }
                continue;
            }

            if (shouldSkipDescriptionLine(line, title)) {
                continue;
            }
            if (line.isBlank()) {
                if (paragraph.length() >= 80) {
                    break;
                }
                paragraph.setLength(0);
                continue;
            }
            if (paragraph.length() > 0) {
                paragraph.append(' ');
            }
            paragraph.append(line);
            if (paragraph.length() >= DESCRIPTION_MAX_LENGTH) {
                break;
            }
        }

        if (paragraph.length() == 0) {
            return title + " 원문 마크다운이 저장된 도서입니다.";
        }

        return truncate(paragraph.toString(), DESCRIPTION_MAX_LENGTH);
    }

    private boolean shouldSkipDescriptionLine(String line, String title) {
        if (line.isBlank()) {
            return false;
        }
        if (line.startsWith("#") || line.startsWith("**") || line.equals("---")) {
            return true;
        }
        if (line.equals(title) || line.equalsIgnoreCase("CONTENTS") || line.equals("目录")) {
            return true;
        }
        if (line.length() < 3) {
            return true;
        }
        return DESCRIPTION_SKIP_PHRASES.stream().anyMatch(line::contains);
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3).trim() + "...";
    }

    private String toGenreCode(String genreName) {
        String normalized = sanitizeValue(genreName).toUpperCase(Locale.ROOT);
        normalized = normalized.replace('&', ' ');
        normalized = normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}가-힣]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 96) + "_" + sha256(normalized).substring(0, 23);
    }

    private String toGenreDisplayName(String genreName) {
        String normalized = sanitizeValue(genreName);
        if (normalized == null || normalized.isBlank()) {
            return "UNKNOWN";
        }
        if (normalized.length() <= LEGACY_SAFE_GENRE_NAME_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, LEGACY_SAFE_GENRE_NAME_LENGTH - 3).trim() + "...";
    }

    private String sha256(String seed) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : digest) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
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

    private record ParsedMdBook(
            String sourceBookId,
            String title,
            String authorName,
            String sourceUrl,
            List<String> genreValues,
            String description,
            String contentMarkdown
    ) {
    }
}





