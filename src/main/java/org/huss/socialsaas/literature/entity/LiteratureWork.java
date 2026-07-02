package org.huss.socialsaas.literature.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.huss.socialsaas.global.common.BaseTimeEntity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Entity
@Table(name = "literature_works")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiteratureWork extends BaseTimeEntity {

    private static final int TITLE_MAX_LENGTH = 255;
    private static final int AUTHOR_NAME_MAX_LENGTH = 150;
    private static final int COUNTRY_MAX_LENGTH = 100;
    private static final int TRANSLATED_LANGUAGE_MAX_LENGTH = 100;
    private static final int AUTHOR_NAME_KOREAN_MAX_LENGTH = 150;
    private static final int TRANSLATOR_MAX_LENGTH = 150;
    private static final int PUBLISHER_MAX_LENGTH = 150;
    private static final int ISBN_MAX_LENGTH = 50;
    private static final int SOURCE_BOOK_ID_MAX_LENGTH = 50;
    private static final int SOURCE_URL_MAX_LENGTH = 500;
    private static final int COVER_IMAGE_URL_MAX_LENGTH = 500;
    private static final int SOURCE_TYPE_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "author_name", nullable = false, length = 150)
    private String authorName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "original_title", length = 255)
    private String originalTitle;

    @Column(length = 100)
    private String country;

    @Column(name = "translated_language", length = 100)
    private String translatedLanguage;

    @Column(name = "author_name_korean", length = 150)
    private String authorNameKorean;

    @Column(length = 150)
    private String translator;

    @Column(name = "translation_publication_support", columnDefinition = "TEXT")
    private String translationPublicationSupport;

    @Column(length = 150)
    private String publisher;

    @Column(length = 50)
    private String isbn;

    @Column(name = "source_book_id", unique = true, length = 50)
    private String sourceBookId;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Lob
    @Column(name = "content_markdown", columnDefinition = "LONGTEXT")
    private String contentMarkdown;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "published_year")
    private Long publishedYear;

    @Column(name = "source_type", length = 30)
    private String sourceType;

    @Column(name = "content_available", nullable = false)
    private boolean contentAvailable;

    @Column(nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "literatureWork", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private final Set<LiteratureWorkGenre> genreMappings = new LinkedHashSet<>();

    @Builder
    private LiteratureWork(String title, String authorName, String description, String coverImageUrl, Long publishedYear, boolean active) {
        this.title = fit(title, TITLE_MAX_LENGTH);
        this.authorName = fit(authorName, AUTHOR_NAME_MAX_LENGTH);
        this.description = description;
        this.coverImageUrl = fit(coverImageUrl, COVER_IMAGE_URL_MAX_LENGTH);
        this.publishedYear = publishedYear;
        this.sourceType = fit("MANUAL", SOURCE_TYPE_MAX_LENGTH);
        this.contentAvailable = false;
        this.active = active;
    }

    public static LiteratureWork create(String title, String authorName, String description, String coverImageUrl, Long publishedYear) {
        return LiteratureWork.builder()
                .title(title)
                .authorName(authorName)
                .description(description)
                .coverImageUrl(coverImageUrl)
                .publishedYear(publishedYear)
                .active(true)
                .build();
    }

    public void applyImportedContent(String sourceBookId, String sourceUrl, String title, String authorName, String description, String contentMarkdown) {
        this.sourceBookId = fit(sourceBookId, SOURCE_BOOK_ID_MAX_LENGTH);
        this.sourceUrl = fit(sourceUrl, SOURCE_URL_MAX_LENGTH);
        this.title = fit(title, TITLE_MAX_LENGTH);
        this.authorName = fit(authorName, AUTHOR_NAME_MAX_LENGTH);
        this.description = description;
        this.contentMarkdown = contentMarkdown;
        this.sourceType = fit("MD_FULLTEXT", SOURCE_TYPE_MAX_LENGTH);
        this.contentAvailable = contentMarkdown != null && !contentMarkdown.isBlank();
        this.active = true;
    }

    public void applyImportedMetadata(
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
            String isbn,
            String description
    ) {
        this.sourceBookId = fit(sourceBookId, SOURCE_BOOK_ID_MAX_LENGTH);
        this.title = fit(title, TITLE_MAX_LENGTH);
        this.originalTitle = fit(originalTitle, TITLE_MAX_LENGTH);
        this.country = fit(country, COUNTRY_MAX_LENGTH);
        this.translatedLanguage = fit(translatedLanguage, TRANSLATED_LANGUAGE_MAX_LENGTH);
        this.authorName = fit(authorName, AUTHOR_NAME_MAX_LENGTH);
        this.authorNameKorean = fit(authorNameKorean, AUTHOR_NAME_KOREAN_MAX_LENGTH);
        this.translator = fit(translator, TRANSLATOR_MAX_LENGTH);
        this.translationPublicationSupport = translationPublicationSupport;
        this.publisher = fit(publisher, PUBLISHER_MAX_LENGTH);
        this.publishedYear = publishedYear;
        this.isbn = fit(isbn, ISBN_MAX_LENGTH);
        this.description = description;
        this.contentMarkdown = null;
        this.sourceUrl = null;
        this.sourceType = fit("XLSX_METADATA", SOURCE_TYPE_MAX_LENGTH);
        this.contentAvailable = false;
        this.active = true;
    }

    public void addGenre(Genre genre) {
        boolean alreadyMapped = this.genreMappings.stream()
                .anyMatch(mapping -> mapping.getGenre().getCode().equalsIgnoreCase(genre.getCode()));
        if (alreadyMapped) {
            return;
        }
        this.genreMappings.add(new LiteratureWorkGenre(this, genre));
    }

    public Set<LiteratureWorkGenre> getGenreMappingsView() {
        return Collections.unmodifiableSet(this.genreMappings);
    }

    private String fit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}





