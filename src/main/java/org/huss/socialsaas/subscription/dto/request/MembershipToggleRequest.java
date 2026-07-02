package org.huss.socialsaas.subscription.dto.request;

import jakarta.validation.constraints.NotNull;

public record MembershipToggleRequest(
        @NotNull(message = "membershipActive 값은 필수입니다.")
        Boolean membershipActive
) {
}
