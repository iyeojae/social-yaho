package org.huss.socialsaas.user;

import org.huss.socialsaas.subscription.dto.request.MembershipToggleRequest;
import org.huss.socialsaas.subscription.dto.response.MembershipResponse;
import org.huss.socialsaas.subscription.service.SubscriptionService;
import org.huss.socialsaas.user.dto.request.CreateUserRequest;
import org.huss.socialsaas.user.dto.request.UpdateUserRequest;
import org.huss.socialsaas.user.dto.response.UserProfileResponse;
import org.huss.socialsaas.user.repository.UserRepository;
import org.huss.socialsaas.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class UserSubscriptionIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void createUserAndToggleMembership() {
        UserProfileResponse createdUser = userService.createUser(
                new CreateUserRequest("reader1@example.com", "password123", "reader1")
        );

        assertEquals("reader1@example.com", createdUser.email());
        assertFalse(createdUser.membershipActive());

        UserProfileResponse updatedUser = userService.updateMyProfile(
                createdUser.id(),
                new UpdateUserRequest("reader1-renewed")
        );

        assertEquals("reader1-renewed", updatedUser.nickname());

        MembershipResponse membershipResponse = subscriptionService.updateMembership(
                createdUser.id(),
                new MembershipToggleRequest(true)
        );

        assertEquals(createdUser.id(), membershipResponse.userId());
        assertTrue(membershipResponse.membershipActive());
        assertTrue(userService.getMyProfile(createdUser.id()).membershipActive());
    }
}

