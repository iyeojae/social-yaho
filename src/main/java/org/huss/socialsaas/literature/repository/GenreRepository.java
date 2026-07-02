package org.huss.socialsaas.literature.repository;

import org.huss.socialsaas.literature.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {

    Optional<Genre> findByCodeIgnoreCase(String code);
}
