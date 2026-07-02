package org.huss.socialsaas.recommendation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.huss.socialsaas.global.response.ApiResponse;
import org.huss.socialsaas.recommendation.dto.request.RecommendationRefreshRequest;
import org.huss.socialsaas.recommendation.dto.response.RecommendationFeedResponse;
import org.huss.socialsaas.recommendation.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private static final String USER_ID_HEADER = "X-USER-ID";

    private final RecommendationService recommendationService;

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<RecommendationFeedResponse>> getFeed(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "추천 피드를 조회했습니다.",
                recommendationService.getFeed(userId, limit)
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RecommendationFeedResponse>> refreshFeed(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @Valid @RequestBody(required = false) RecommendationRefreshRequest request
    ) {
        Integer limit = request == null ? null : request.limit();
        return ResponseEntity.ok(ApiResponse.success(
                "추천 피드를 새로 생성했습니다.",
                recommendationService.refreshFeed(userId, limit)
        ));
    }
}

