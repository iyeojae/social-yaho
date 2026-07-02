package org.huss.socialsaas.preference.service;

import lombok.RequiredArgsConstructor;
import org.huss.socialsaas.interaction.entity.InteractionType;
import org.huss.socialsaas.literature.entity.Genre;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.huss.socialsaas.literature.entity.LiteratureWorkGenre;
import org.huss.socialsaas.preference.entity.UserGenrePreference;
import org.huss.socialsaas.preference.repository.UserGenrePreferenceRepository;
import org.huss.socialsaas.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPreferenceService {

    private final UserGenrePreferenceRepository userGenrePreferenceRepository;

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


