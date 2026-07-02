package org.huss.socialsaas.literature.controller;

import lombok.RequiredArgsConstructor;
import org.huss.socialsaas.global.response.ApiResponse;
import org.huss.socialsaas.literature.dto.response.GenreResponse;
import org.huss.socialsaas.literature.dto.response.LiteratureDetailResponse;
import org.huss.socialsaas.literature.dto.response.LiteratureSummaryResponse;
import org.huss.socialsaas.literature.service.LiteratureService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class LiteratureController {

    private final LiteratureService literatureService;

    @GetMapping("/books")
    public ResponseEntity<ApiResponse<List<LiteratureSummaryResponse>>> getBooks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String genreCode
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "도서 목록을 조회했습니다.",
                literatureService.getBooks(keyword, genreCode)
        ));
    }

    @GetMapping("/books/{bookId}")
    public ResponseEntity<ApiResponse<LiteratureDetailResponse>> getBook(@PathVariable Long bookId) {
        return ResponseEntity.ok(ApiResponse.success(
                "도서 상세를 조회했습니다.",
                literatureService.getBook(bookId)
        ));
    }

    @GetMapping("/genres")
    public ResponseEntity<ApiResponse<List<GenreResponse>>> getGenres() {
        return ResponseEntity.ok(ApiResponse.success(
                "장르 목록을 조회했습니다.",
                literatureService.getGenres()
        ));
    }
}

