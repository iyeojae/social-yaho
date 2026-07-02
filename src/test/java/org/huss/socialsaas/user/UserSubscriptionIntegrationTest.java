package org.huss.socialsaas.user;

import org.huss.socialsaas.subscription.dto.request.MembershipToggleRequest;
import org.huss.socialsaas.subscription.dto.response.MembershipResponse;
import org.huss.socialsaas.subscription.service.SubscriptionService;
import org.huss.socialsaas.literature.entity.Genre;
import org.huss.socialsaas.literature.repository.GenreRepository;
import org.huss.socialsaas.preference.entity.UserGenrePreference;
import org.huss.socialsaas.preference.repository.UserGenrePreferenceRepository;
import org.huss.socialsaas.user.dto.request.CreateUserRequest;
import org.huss.socialsaas.user.dto.request.UpdateUserRequest;
import org.huss.socialsaas.user.dto.response.UserProfileResponse;
import org.huss.socialsaas.user.repository.UserRepository;
import org.huss.socialsaas.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

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

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private UserGenrePreferenceRepository userGenrePreferenceRepository;

    @BeforeEach
    void setUp() {
        genreRepository.save(Genre.builder()
                .code("CLASSIC")
                .name("고전문학")
                .description("한국 고전 및 근대 문학")
                .build());
    }

    @AfterEach
    void tearDown() {
        userGenrePreferenceRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        userRepository.deleteAll();
    }

    @Test
    void createUserAndToggleMembership() {
        UserProfileResponse createdUser = userService.createUser(
                new CreateUserRequest("reader1@example.com", "password123", "reader1", null, null, null)
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

    @Test
    void createUserWithSurveyInitializesExplicitGenrePreference() {
        UserProfileResponse createdUser = userService.createUser(
                new CreateUserRequest(
                        "reader-survey@example.com",
                        "password123",
                        "reader-survey",
                        List.of("CLASSIC"),
                        null,
                        null
                )
        );

        List<UserGenrePreference> preferences = userGenrePreferenceRepository
                .findTop5ByUserIdOrderByTotalScoreDescUpdatedAtDesc(createdUser.id());

        assertEquals(1, preferences.size());
        assertEquals("CLASSIC", preferences.get(0).getGenre().getCode());
        assertEquals(10L, preferences.get(0).getExplicitScore());
        assertEquals(0L, preferences.get(0).getImplicitScore());
        assertEquals(10L, preferences.get(0).getTotalScore());
    }

    @Test
    void createUserWithPsychologicalSurveyInfersGenrePreference() {
        UserProfileResponse createdUser = userService.createUser(
                new CreateUserRequest(
                        "reader-psychology@example.com",
                        "password123",
                        "reader-psychology",
                        null,
                        "I am most drawn to quiet, lyrical stories that stay with me emotionally.",
                        "A reflective poetry film about grief and memory stayed with me for weeks."
                )
        );

        List<UserGenrePreference> preferences = userGenrePreferenceRepository
                .findTop5ByUserIdOrderByTotalScoreDescUpdatedAtDesc(createdUser.id());

        assertFalse(preferences.isEmpty());
        assertEquals("CLASSIC", preferences.get(0).getGenre().getCode());
        assertEquals(6L, preferences.get(0).getExplicitScore());
        assertEquals(0L, preferences.get(0).getImplicitScore());
        assertEquals(6L, preferences.get(0).getTotalScore());
    }
}

