package org.huss.socialsaas.interaction;

import org.huss.socialsaas.interaction.dto.request.InteractionCreateRequest;
import org.huss.socialsaas.interaction.dto.response.InteractionResponse;
import org.huss.socialsaas.interaction.entity.InteractionType;
import org.huss.socialsaas.interaction.repository.UserInteractionEventRepository;
import org.huss.socialsaas.interaction.service.InteractionService;
import org.huss.socialsaas.literature.entity.Genre;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.huss.socialsaas.literature.repository.GenreRepository;
import org.huss.socialsaas.literature.repository.LiteratureWorkGenreRepository;
import org.huss.socialsaas.literature.repository.LiteratureWorkRepository;
import org.huss.socialsaas.preference.entity.UserGenrePreference;
import org.huss.socialsaas.preference.repository.UserGenrePreferenceRepository;
import org.huss.socialsaas.user.dto.request.CreateUserRequest;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class InteractionPreferenceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private InteractionService interactionService;

    @Autowired
    private UserGenrePreferenceRepository userGenrePreferenceRepository;

    @Autowired
    private UserInteractionEventRepository userInteractionEventRepository;

    @Autowired
    private LiteratureWorkRepository literatureWorkRepository;

    @Autowired
    private LiteratureWorkGenreRepository literatureWorkGenreRepository;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private UserRepository userRepository;

    private Long userId;
    private Long bookId;

    @BeforeEach
    void setUp() {
        UserProfileResponse user = userService.createUser(
                new CreateUserRequest("reader2@example.com", "password123", "reader2", null)
        );
        userId = user.id();

        Genre genre = genreRepository.save(Genre.builder()
                .code("POETRY")
                .name("시")
                .description("서정성과 운율이 있는 문학")
                .build());

        LiteratureWork work = LiteratureWork.create(
                "하늘과 바람과 별과 시",
                "윤동주",
                "한국 현대시를 대표하는 작품집",
                "https://example.com/books/2.jpg",
                1948L
        );
        work.addGenre(genre);
        bookId = literatureWorkRepository.saveAndFlush(work).getId();
    }

    @AfterEach
    void tearDown() {
        userGenrePreferenceRepository.deleteAllInBatch();
        userInteractionEventRepository.deleteAllInBatch();
        literatureWorkGenreRepository.deleteAllInBatch();
        literatureWorkRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void createInteractionAndAggregateGenrePreference() {
        InteractionResponse response = interactionService.createInteraction(
                userId,
                new InteractionCreateRequest(bookId, InteractionType.LIKE, null, null, "DETAIL")
        );

        assertNotNull(response.eventId());
        assertEquals(userId, response.userId());
        assertEquals(bookId, response.bookId());
        assertEquals("LIKE", response.interactionType());

        List<UserGenrePreference> preferences = userGenrePreferenceRepository.findTop5ByUserIdOrderByTotalScoreDescUpdatedAtDesc(userId);
        assertEquals(1, preferences.size());
        assertEquals("POETRY", preferences.get(0).getGenre().getCode());
        assertEquals(4L, preferences.get(0).getImplicitScore());
        assertEquals(4L, preferences.get(0).getTotalScore());
    }
}
