package org.huss.socialsaas.subscription.dto.response;

import org.huss.socialsaas.user.entity.User;

import java.time.Instant;

public record MembershipResponse(
        Long userId,
        boolean membershipActive,
        Instant updatedAt
) {

    public static MembershipResponse from(User user) {
        return new MembershipResponse(user.getId(), user.isMembershipActive(), user.getUpdatedAt());
    }
}

