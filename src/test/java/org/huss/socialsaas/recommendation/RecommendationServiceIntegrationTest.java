package org.huss.socialsaas.recommendation;

import org.huss.socialsaas.ai.entity.BookAiTag;
import org.huss.socialsaas.ai.repository.BookAiTagRepository;
import org.huss.socialsaas.interaction.dto.request.InteractionCreateRequest;
import org.huss.socialsaas.interaction.entity.InteractionType;
import org.huss.socialsaas.interaction.repository.UserInteractionEventRepository;
import org.huss.socialsaas.interaction.service.InteractionService;
import org.huss.socialsaas.literature.entity.Genre;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.huss.socialsaas.literature.repository.GenreRepository;
import org.huss.socialsaas.literature.repository.LiteratureWorkGenreRepository;
import org.huss.socialsaas.literature.repository.LiteratureWorkRepository;
import org.huss.socialsaas.preference.repository.UserGenrePreferenceRepository;
import org.huss.socialsaas.recommendation.dto.response.RecommendationFeedResponse;
import org.huss.socialsaas.recommendation.dto.response.RecommendationReasonResponse;
import org.huss.socialsaas.recommendation.service.RecommendationService;
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

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class RecommendationServiceIntegrationTest {

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private InteractionService interactionService;

    @Autowired
    private UserService userService;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private LiteratureWorkRepository literatureWorkRepository;

    @Autowired
    private LiteratureWorkGenreRepository literatureWorkGenreRepository;

    @Autowired
    private BookAiTagRepository bookAiTagRepository;

    @Autowired
    private UserGenrePreferenceRepository userGenrePreferenceRepository;

    @Autowired
    private UserInteractionEventRepository userInteractionEventRepository;

    @Autowired
    private UserRepository userRepository;

    private Long userId;
    private Long recentClassicBookId;
    private Long recommendedClassicBookId;
    private Long poetryBookId;

    @BeforeEach
    void setUp() {
        UserProfileResponse user = userService.createUser(
                new CreateUserRequest("reader3@example.com", "password123", "reader3")
        );
        userId = user.id();

        Genre classic = genreRepository.save(Genre.builder()
                .code("CLASSIC")
                .name("고전문학")
                .description("한국 고전 및 근대 문학")
                .build());
        Genre poetry = genreRepository.save(Genre.builder()
                .code("POETRY")
                .name("시")
                .description("시와 운문")
                .build());

        LiteratureWork recentClassicBook = LiteratureWork.create(
                "운수 좋은 날",
                "현진건",
                "현실 비극을 담은 단편소설",
                "https://example.com/books/11.jpg",
                1924L
        );
        recentClassicBook.addGenre(classic);
        recentClassicBookId = literatureWorkRepository.saveAndFlush(recentClassicBook).getId();

        LiteratureWork recommendedClassicBook = LiteratureWork.create(
                "메밀꽃 필 무렵",
                "이효석",
                "자연과 인간의 정서를 서정적으로 그린 단편소설",
                "https://example.com/books/12.jpg",
                1936L
        );
        recommendedClassicBook.addGenre(classic);
        recommendedClassicBookId = literatureWorkRepository.saveAndFlush(recommendedClassicBook).getId();

        LiteratureWork poetryBook = LiteratureWork.create(
                "하늘과 바람과 별과 시",
                "윤동주",
                "한국 현대시를 대표하는 작품집",
                "https://example.com/books/13.jpg",
                1948L
        );
        poetryBook.addGenre(poetry);
        poetryBookId = literatureWorkRepository.saveAndFlush(poetryBook).getId();

        bookAiTagRepository.save(BookAiTag.create(
                recommendedClassicBook,
                "한국 단편문학의 정서를 담은 작품입니다.",
                "최근 읽은 고전문학과 결이 비슷해 추천합니다.",
                "classic,short-story,k-literature",
                Instant.now()
        ));
        bookAiTagRepository.save(BookAiTag.create(
                poetryBook,
                "서정적이고 사색적인 시집입니다.",
                "감성적인 문체를 좋아하는 독자에게 어울립니다.",
                "poetry,lyrical,reflection",
                Instant.now()
        ));

        interactionService.createInteraction(
                userId,
                new InteractionCreateRequest(recentClassicBookId, InteractionType.LIKE, null, null, "DETAIL")
        );
    }

    @AfterEach
    void tearDown() {
        bookAiTagRepository.deleteAllInBatch();
        userGenrePreferenceRepository.deleteAllInBatch();
        userInteractionEventRepository.deleteAllInBatch();
        literatureWorkGenreRepository.deleteAllInBatch();
        literatureWorkRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void getFeedCachesAndRefreshesRecommendationFeed() {
        RecommendationFeedResponse first = recommendationService.getFeed(userId, 10);
        assertFalse(first.cached());
        assertEquals(1, first.items().size());
        assertEquals(recommendedClassicBookId, first.items().get(0).bookId());
        assertTrue(first.items().get(0).genres().contains("CLASSIC"));
        assertEquals("PREGENERATED_BOOK_AI_TAG", first.items().get(0).sourceSignals().reasonType());

        RecommendationFeedResponse second = recommendationService.getFeed(userId, 10);
        assertTrue(second.cached());
        assertEquals(first.items().get(0).bookId(), second.items().get(0).bookId());

        interactionService.createInteraction(
                userId,
                new InteractionCreateRequest(poetryBookId, InteractionType.BOOKMARK, null, null, "DETAIL")
        );

        RecommendationFeedResponse third = recommendationService.getFeed(userId, 10);
        assertFalse(third.cached());
        assertEquals(1, third.items().size());
    }

    @Test
    void getRecommendationReasonBuildsPersonalizedTextFromRecentReadsAndAiTag() {
        RecommendationReasonResponse response = recommendationService.getRecommendationReason(userId, recommendedClassicBookId);

        assertEquals(userId, response.userId());
        assertEquals(recommendedClassicBookId, response.bookId());
        assertEquals("메밀꽃 필 무렵", response.bookTitle());
        assertTrue(response.personalizedReasonText().contains("운수 좋은 날"));
        assertTrue(response.personalizedReasonText().contains("메밀꽃 필 무렵"));
        assertTrue(response.personalizedReasonText().contains("고전문학"));
        assertEquals("최근 읽은 고전문학과 결이 비슷해 추천합니다.", response.aiReasonText());
        assertTrue(response.keywordTags().contains("classic"));
        assertEquals("PREGENERATED_BOOK_AI_TAG", response.reasonType());
        assertEquals(1, response.matchedGenres().size());
        assertEquals("CLASSIC", response.matchedGenres().get(0).code());
        assertEquals(1, response.recentReadBooks().size());
        assertEquals(recentClassicBookId, response.recentReadBooks().get(0).bookId());
        assertTrue(response.recentReadBooks().get(0).matchedGenreCodes().contains("CLASSIC"));
    }
}


