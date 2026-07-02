package org.huss.socialsaas.interaction.dto.response;

import org.huss.socialsaas.interaction.entity.UserInteractionEvent;

import java.time.Instant;

public record InteractionResponse(
        Long eventId,
        Long userId,
        Long bookId,
        String interactionType,
        Long viewDurationSeconds,
        Long progressPercent,
        String sourceScreen,
        Instant createdAt
) {

    public static InteractionResponse from(UserInteractionEvent event) {
        return new InteractionResponse(
                event.getId(),
                event.getUser().getId(),
                event.getLiteratureWork().getId(),
                event.getInteractionType().name(),
                event.getViewDurationSeconds(),
                event.getProgressPercent(),
                event.getSourceScreen(),
                event.getCreatedAt()
        );
    }
}

