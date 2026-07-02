package org.huss.socialsaas.subscription.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.huss.socialsaas.global.response.ApiResponse;
import org.huss.socialsaas.subscription.dto.request.MembershipToggleRequest;
import org.huss.socialsaas.subscription.dto.response.MembershipResponse;
import org.huss.socialsaas.subscription.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/membership")
public class SubscribeController {

    private static final String USER_ID_HEADER = "X-USER-ID";

    private final SubscriptionService subscriptionService;

    @PatchMapping
    public ResponseEntity<ApiResponse<MembershipResponse>> updateMembership(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @Valid @RequestBody MembershipToggleRequest request
    ) {
        MembershipResponse response = subscriptionService.updateMembership(userId, request);
        return ResponseEntity.ok(ApiResponse.success("멤버십 상태를 변경했습니다.", response));
    }
}

