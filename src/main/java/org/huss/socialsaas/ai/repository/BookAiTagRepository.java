package org.huss.socialsaas.ai.repository;

import org.huss.socialsaas.ai.entity.BookAiTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookAiTagRepository extends JpaRepository<BookAiTag, Long> {

    Optional<BookAiTag> findByLiteratureWorkId(Long literatureWorkId);
}

