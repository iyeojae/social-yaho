package org.huss.socialsaas.preference.service;

import lombok.RequiredArgsConstructor;
import org.huss.socialsaas.interaction.entity.InteractionType;
import org.huss.socialsaas.literature.entity.Genre;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.huss.socialsaas.literature.entity.LiteratureWorkGenre;
import org.huss.socialsaas.literature.repository.GenreRepository;
import org.huss.socialsaas.preference.entity.UserGenrePreference;
import org.huss.socialsaas.preference.repository.UserGenrePreferenceRepository;
import org.huss.socialsaas.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPreferenceService {

    private static final long DIRECT_SURVEY_EXPLICIT_SCORE = 10L;
    private static final long INFERRED_SURVEY_EXPLICIT_SCORE = 6L;

    private final UserGenrePreferenceRepository userGenrePreferenceRepository;
    private final GenreRepository genreRepository;

    @Transactional
    public void initializePreferencesFromSurvey(User user, List<String> preferredGenreCodes) {
        initializePreferencesFromSurvey(user, preferredGenreCodes, List.of());
    }

    @Transactional
    public void initializePreferencesFromSurvey(
            User user,
            List<String> selectedGenreCodes,
            List<String> inferredGenreCodes
    ) {
        if ((selectedGenreCodes == null || selectedGenreCodes.isEmpty())
                && (inferredGenreCodes == null || inferredGenreCodes.isEmpty())) {
            return;
        }

        applySurveyCodes(user, selectedGenreCodes, DIRECT_SURVEY_EXPLICIT_SCORE);
        applySurveyCodes(user, inferredGenreCodes, INFERRED_SURVEY_EXPLICIT_SCORE);
    }

    private void applySurveyCodes(User user, List<String> genreCodes, long score) {
        if (genreCodes == null || genreCodes.isEmpty()) {
            return;
        }

        Set<String> normalizedCodes = genreCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        for (String genreCode : normalizedCodes) {
            genreRepository.findByCodeIgnoreCase(genreCode)
                    .ifPresent(genre -> upsertSurveyPreference(user, genre, score));
        }
    }

    @Transactional
    public void reflectInteraction(User user, LiteratureWork literatureWork, InteractionType interactionType) {
        long score = resolveImplicitScore(interactionType);
        if (score == 0L) {
            return;
        }

        for (LiteratureWorkGenre genreMapping : literatureWork.getGenreMappingsView()) {
            Genre genre = genreMapping.getGenre();
            UserGenrePreference preference = userGenrePreferenceRepository.findByUserAndGenre(user, genre)
                    .orElseGet(() -> userGenrePreferenceRepository.save(UserGenrePreference.create(user, genre)));
            preference.addImplicitScore(score);
        }
    }

    private void upsertSurveyPreference(User user, Genre genre, long score) {
        UserGenrePreference preference = userGenrePreferenceRepository.findByUserAndGenre(user, genre)
                .orElseGet(() -> userGenrePreferenceRepository.save(UserGenrePreference.create(user, genre)));
        preference.addExplicitScore(score);
    }

    private long resolveImplicitScore(InteractionType interactionType) {
        return switch (interactionType) {
            case VIEW -> 1L;
            case READ_START -> 2L;
            case READ_COMPLETE -> 5L;
            case LIKE -> 4L;
            case UNLIKE -> -2L;
            case BOOKMARK -> 3L;
            case UNBOOKMARK -> -1L;
        };
    }
}

