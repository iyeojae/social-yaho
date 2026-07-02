package org.huss.socialsaas.interaction.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.huss.socialsaas.interaction.entity.InteractionType;

public record InteractionCreateRequest(
        @NotNull(message = "bookId는 필수입니다.")
        Long bookId,

        @NotNull(message = "interactionType은 필수입니다.")
        InteractionType interactionType,

        @PositiveOrZero(message = "viewDurationSeconds는 0 이상이어야 합니다.")
        Long viewDurationSeconds,

        @Min(value = 0, message = "progressPercent는 0 이상이어야 합니다.")
        @Max(value = 100, message = "progressPercent는 100 이하여야 합니다.")
        Long progressPercent,

        @Size(max = 50, message = "sourceScreen은 50자 이하로 입력해주세요.")
        String sourceScreen
) {
}
