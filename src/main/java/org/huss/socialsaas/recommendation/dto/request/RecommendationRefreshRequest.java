package org.huss.socialsaas.recommendation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RecommendationRefreshRequest(
        @Min(value = 1, message = "limit은 1 이상이어야 합니다.")
        @Max(value = 50, message = "limit은 50 이하여야 합니다.")
        Integer limit
) {
}

