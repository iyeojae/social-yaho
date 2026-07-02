package org.huss.socialsaas.subscription.service;

import lombok.RequiredArgsConstructor;
import org.huss.socialsaas.subscription.dto.request.MembershipToggleRequest;
import org.huss.socialsaas.subscription.dto.response.MembershipResponse;
import org.huss.socialsaas.user.entity.User;
import org.huss.socialsaas.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final UserService userService;

    @Transactional
    public MembershipResponse updateMembership(Long userId, MembershipToggleRequest request) {
        User user = userService.getUser(userId);
        user.updateMembership(request.membershipActive());
        return MembershipResponse.from(user);
    }
}
