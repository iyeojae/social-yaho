package org.huss.socialsaas.preference.repository;

import org.huss.socialsaas.literature.entity.Genre;
import org.huss.socialsaas.preference.entity.UserGenrePreference;
import org.huss.socialsaas.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserGenrePreferenceRepository extends JpaRepository<UserGenrePreference, Long> {

    Optional<UserGenrePreference> findByUserAndGenre(User user, Genre genre);

    @EntityGraph(attributePaths = {"genre"})
    List<UserGenrePreference> findTop5ByUserIdOrderByTotalScoreDescUpdatedAtDesc(Long userId);
}

