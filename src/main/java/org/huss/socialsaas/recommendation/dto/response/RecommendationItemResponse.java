package org.huss.socialsaas.recommendation.dto.response;

import java.util.List;

public record RecommendationItemResponse(
        int rank,
        Long bookId,
        String title,
        String authorName,
        String coverImageUrl,
        List<String> genres,
        String reasonText,
        List<String> keywordTags,
        long score,
        RecommendationSourceSignals sourceSignals
) {

    public record RecommendationSourceSignals(
            List<String> matchedGenres,
            List<Long> recentReadBookIds,
            String reasonType
    ) {
    }
}

