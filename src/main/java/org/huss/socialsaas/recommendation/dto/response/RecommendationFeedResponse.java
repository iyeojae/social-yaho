package org.huss.socialsaas.recommendation.dto.response;

import java.time.Instant;
import java.util.List;

public record RecommendationFeedResponse(
        Long userId,
        String cacheKey,
        boolean cached,
        Instant generatedAt,
        Instant expiresAt,
        int limit,
        List<RecommendationItemResponse> items
) {
}
