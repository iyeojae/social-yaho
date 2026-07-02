package org.huss.socialsaas.user.dto.response;

import org.huss.socialsaas.user.entity.User;

import java.time.Instant;

public record UserProfileResponse(
        Long id,
        String email,
        String nickname,
        boolean membershipActive,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.isMembershipActive(),
                user.getStatus().name(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
