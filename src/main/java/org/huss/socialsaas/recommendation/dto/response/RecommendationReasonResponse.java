package org.huss.socialsaas.recommendation.dto.response;

import java.time.Instant;
import java.util.List;

public record RecommendationReasonResponse(
        Long userId,
        Long bookId,
        String bookTitle,
        String personalizedReasonText,
        String aiReasonText,
        List<String> keywordTags,
        List<MatchedGenre> matchedGenres,
        List<RecentReadBook> recentReadBooks,
        String reasonType,
        Instant generatedAt
) {

    public record MatchedGenre(
            String code,
            String name
    ) {
    }

    public record RecentReadBook(
            Long bookId,
            String title,
            String authorName,
            List<String> matchedGenreCodes
    ) {
    }
}

