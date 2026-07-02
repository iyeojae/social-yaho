package org.huss.socialsaas.literature.service;

import lombok.RequiredArgsConstructor;
import org.huss.socialsaas.ai.service.BookAiSummaryService;
import org.huss.socialsaas.global.exception.BusinessException;
import org.huss.socialsaas.global.exception.ErrorCode;
import org.huss.socialsaas.literature.dto.response.GenreResponse;
import org.huss.socialsaas.literature.dto.response.LiteratureDetailResponse;
import org.huss.socialsaas.literature.dto.response.LiteratureSummaryResponse;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.huss.socialsaas.literature.repository.GenreRepository;
import org.huss.socialsaas.literature.repository.LiteratureWorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiteratureService {

    private final LiteratureWorkRepository literatureWorkRepository;
    private final GenreRepository genreRepository;
    private final BookAiSummaryService bookAiSummaryService;

    public List<LiteratureSummaryResponse> getBooks(String keyword, String genreCode) {
        String normalizedKeyword = normalize(keyword);
        String normalizedGenreCode = normalize(genreCode);

        return literatureWorkRepository.searchActiveWorks(normalizedKeyword, normalizedGenreCode)
                .stream()
                .map(LiteratureSummaryResponse::from)
                .toList();
    }

    public LiteratureDetailResponse getBook(Long bookId) {
        return literatureWorkRepository.findDetailById(bookId)
                .map(this::toDetailResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));
    }

    public List<GenreResponse> getGenres() {
        return genreRepository.findAll()
                .stream()
                .map(GenreResponse::from)
                .toList();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private LiteratureDetailResponse toDetailResponse(LiteratureWork book) {
        if (!isAiSummaryCandidate(book)) {
            return LiteratureDetailResponse.from(book);
        }

        String aiSummary = bookAiSummaryService.getOrGenerateSummary(book).orElse(null);
        String displayDescription = aiSummary != null && !aiSummary.isBlank()
                ? aiSummary
                : book.getDescription();
        String descriptionSource = aiSummary != null && !aiSummary.isBlank()
                ? "AI_GENERATED_SUMMARY"
                : "IMPORTED_METADATA_DESCRIPTION";

        return LiteratureDetailResponse.from(book, displayDescription, aiSummary, descriptionSource);
    }

    private boolean isAiSummaryCandidate(LiteratureWork book) {
        return !book.isContentAvailable() && "XLSX_METADATA".equalsIgnoreCase(book.getSourceType());
    }
}


