package org.huss.socialsaas.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.huss.socialsaas.ai.entity.BookAiTag;
import org.huss.socialsaas.ai.repository.BookAiTagRepository;
import org.huss.socialsaas.interaction.repository.UserInteractionEventRepository;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.huss.socialsaas.literature.entity.LiteratureWorkGenre;
import org.huss.socialsaas.literature.repository.LiteratureWorkRepository;
import org.huss.socialsaas.preference.entity.UserGenrePreference;
import org.huss.socialsaas.preference.repository.UserGenrePreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int RECENT_BOOK_LIMIT = 5;
    private static final String REASON_TYPE = "PREGENERATED_BOOK_AI_TAG";

    private final RecommendationCacheService recommendationCacheService;
    private final UserInteractionEventRepository userInteractionEventRepository;
    private final UserGenrePreferenceRepository userGenrePreferenceRepository;
    private final LiteratureWorkRepository literatureWorkRepository;
    private final BookAiTagRepository bookAiTagRepository;

    public org.huss.socialsaas.recommendation.dto.response.RecommendationFeedResponse getFeed(Long userId, Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        Optional<org.huss.socialsaas.recommendation.dto.response.RecommendationFeedResponse> cached = recommendationCacheService.getUserFeed(userId);
        if (cached.isPresent()) {
            return withCached(cached.get(), true);
        }
        return generateAndCache(userId, normalizedLimit, false);
    }

    @Transactional
    public org.huss.socialsaas.recommendation.dto.response.RecommendationFeedResponse refreshFeed(Long userId, Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        recommendationCacheService.evictUserFeed(userId);
        return generateAndCache(userId, normalizedLimit, false);
    }

    private org.huss.socialsaas.recommendation.dto.response.RecommendationFeedResponse generateAndCache(Long userId, int limit, boolean cached) {
        List<UserGenrePreference> topPreferences = userGenrePreferenceRepository.findTop5ByUserIdOrderByTotalScoreDescUpdatedAtDesc(userId);
        List<Long> recentBookIds = userInteractionEventRepository.findRecentDistinctBookIds(userId, RECENT_BOOK_LIMIT);
        List<LiteratureWork> recentBooks = recentBookIds.isEmpty()
                ? List.of()
                : literatureWorkRepository.findActiveWorksByIds(recentBookIds);

        Set<String> preferredGenreCodes = topPreferences.stream()
                .map(preference -> preference.getGenre().getCode().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> recentGenreCodes = extractGenreCodes(recentBooks);
        Set<String> targetGenreCodes = new LinkedHashSet<>();
        targetGenreCodes.addAll(preferredGenreCodes);
        targetGenreCodes.addAll(recentGenreCodes);

        List<Long> excludedIds = recentBookIds.isEmpty() ? List.of(-1L) : recentBookIds;
        boolean excludedIdsEmpty = recentBookIds.isEmpty();

        List<LiteratureWork> candidates = targetGenreCodes.isEmpty()
                ? literatureWorkRepository.findActiveWorksExcludingIds(excludedIds, excludedIdsEmpty)
                : literatureWorkRepository.findRecommendationCandidates(new ArrayList<>(targetGenreCodes), excludedIds, excludedIdsEmpty);

        Map<Long, Long> preferenceScoreByGenreId = topPreferences.stream()
                .collect(Collectors.toMap(preference -> preference.getGenre().getId(), UserGenrePreference::getTotalScore, Long::max, LinkedHashMap::new));

        List<ScoredCandidate> scoredCandidates = candidates.stream()
                .map(candidate -> scoreCandidate(candidate, preferenceScoreByGenreId, preferredGenreCodes, recentGenreCodes, recentBookIds))
                .sorted(Comparator.comparingLong(ScoredCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.work().getId(), Comparator.reverseOrder()))
                .limit(limit)
                .toList();

        Instant generatedAt = Instant.now();
        Instant expiresAt = generatedAt.plusSeconds(recommendationCacheService.getFeedCacheTtlSeconds());
        String cacheKey = recommendationCacheService.buildUserFeedKey(userId);

        List<org.huss.socialsaas.recommendation.dto.response.RecommendationItemResponse> items = new ArrayList<>();
        for (int index = 0; index < scoredCandidates.size(); index++) {
            items.add(toItemResponse(index + 1, scoredCandidates.get(index), recentBookIds));
        }

        org.huss.socialsaas.recommendation.dto.response.RecommendationFeedResponse response =
                new org.huss.socialsaas.recommendation.dto.response.RecommendationFeedResponse(
                        userId,
                        cacheKey,
                        cached,
                        generatedAt,
                        expiresAt,
                        limit,
                        items
                );

        recommendationCacheService.cacheUserFeed(userId, response);
        return response;
    }

    private org.huss.socialsaas.recommendation.dto.response.RecommendationItemResponse toItemResponse(
            int rank,
            ScoredCandidate candidate,
            List<Long> recentBookIds
    ) {
        LiteratureWork work = candidate.work();
        Optional<BookAiTag> aiTag = bookAiTagRepository.findByLiteratureWorkId(work.getId());
        List<String> genreCodes = work.getGenreMappingsView().stream()
                .map(LiteratureWorkGenre::getGenre)
                .map(genre -> genre.getCode())
                .sorted(String::compareToIgnoreCase)
                .toList();
        String reasonText = aiTag.map(BookAiTag::getRecommendationReason)
                .filter(text -> text != null && !text.isBlank())
                .orElse("사용자의 최근 관심 장르와 잘 맞는 작품입니다.");
        List<String> keywordTags = aiTag.map(BookAiTag::getKeywordTags)
                .map(this::splitTags)
                .orElse(List.of());

        return new org.huss.socialsaas.recommendation.dto.response.RecommendationItemResponse(
                rank,
                work.getId(),
                work.getTitle(),
                work.getAuthorName(),
                work.getCoverImageUrl(),
                genreCodes,
                reasonText,
                keywordTags,
                candidate.score(),
                new org.huss.socialsaas.recommendation.dto.response.RecommendationItemResponse.RecommendationSourceSignals(
                        candidate.matchedGenres().stream().sorted(String::compareToIgnoreCase).toList(),
                        recentBookIds,
                        REASON_TYPE
                )
        );
    }

    private ScoredCandidate scoreCandidate(
            LiteratureWork work,
            Map<Long, Long> preferenceScoreByGenreId,
            Set<String> preferredGenreCodes,
            Set<String> recentGenreCodes,
            List<Long> recentBookIds
    ) {
        Set<String> workGenres = extractGenreCodes(List.of(work));
        Set<String> matchedGenres = new LinkedHashSet<>();
        matchedGenres.addAll(intersection(workGenres, preferredGenreCodes));
        matchedGenres.addAll(intersection(workGenres, recentGenreCodes));

        long preferredScore = work.getGenreMappingsView().stream()
                .map(LiteratureWorkGenre::getGenre)
                .mapToLong(genre -> preferenceScoreByGenreId.getOrDefault(genre.getId(), 0L))
                .sum();

        long matchedPreferredCount = intersection(workGenres, preferredGenreCodes).size();
        long matchedRecentCount = intersection(workGenres, recentGenreCodes).size();
        long score = (matchedPreferredCount * 30L) + (matchedRecentCount * 25L) + Math.min(preferredScore, 30L);

        if (recentBookIds.contains(work.getId())) {
            score -= 1000L;
        }

        return new ScoredCandidate(work, score, matchedGenres);
    }

    private Set<String> extractGenreCodes(Collection<LiteratureWork> works) {
        return works.stream()
                .flatMap(work -> work.getGenreMappingsView().stream())
                .map(LiteratureWorkGenre::getGenre)
                .map(genre -> genre.getCode().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        return left.stream()
                .filter(right::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> splitTags(String rawTags) {
        if (rawTags == null || rawTags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawTags.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private org.huss.socialsaas.recommendation.dto.response.RecommendationFeedResponse withCached(
            org.huss.socialsaas.recommendation.dto.response.RecommendationFeedResponse response,
            boolean cached
    ) {
        return new org.huss.socialsaas.recommendation.dto.response.RecommendationFeedResponse(
                response.userId(),
                response.cacheKey(),
                cached,
                response.generatedAt(),
                response.expiresAt(),
                response.limit(),
                response.items()
        );
    }

    private record ScoredCandidate(LiteratureWork work, long score, Set<String> matchedGenres) {
    }
}

