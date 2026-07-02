package org.huss.socialsaas.literature.repository;

import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LiteratureWorkRepository extends JpaRepository<LiteratureWork, Long> {

    Optional<LiteratureWork> findBySourceBookId(String sourceBookId);

    @Query("""
            select distinct lw
            from LiteratureWork lw
            left join fetch lw.genreMappings gm
            left join fetch gm.genre g
            where lw.active = true
              and (:keyword is null or lower(lw.title) like lower(concat('%', :keyword, '%'))
                   or lower(lw.originalTitle) like lower(concat('%', :keyword, '%'))
                   or lower(lw.authorName) like lower(concat('%', :keyword, '%')))
              and (:genreCode is null or lower(g.code) = lower(:genreCode))
            order by lw.id desc
            """)
    List<LiteratureWork> searchActiveWorks(@Param("keyword") String keyword, @Param("genreCode") String genreCode);

    @Query("""
            select distinct lw
            from LiteratureWork lw
            left join fetch lw.genreMappings gm
            left join fetch gm.genre
            where lw.id = :bookId
              and lw.active = true
            """)
    Optional<LiteratureWork> findDetailById(@Param("bookId") Long bookId);

    @Query("""
            select distinct lw
            from LiteratureWork lw
            join fetch lw.genreMappings gm
            join fetch gm.genre g
            where lw.active = true
              and lower(g.code) in :genreCodes
              and (:excludedIdsEmpty = true or lw.id not in :excludedIds)
            order by lw.id desc
            """)
    List<LiteratureWork> findRecommendationCandidates(
            @Param("genreCodes") List<String> genreCodes,
            @Param("excludedIds") List<Long> excludedIds,
            @Param("excludedIdsEmpty") boolean excludedIdsEmpty
    );

    @Query("""
            select distinct lw
            from LiteratureWork lw
            left join fetch lw.genreMappings gm
            left join fetch gm.genre
            where lw.active = true
              and (:excludedIdsEmpty = true or lw.id not in :excludedIds)
            order by lw.id desc
            """)
    List<LiteratureWork> findActiveWorksExcludingIds(
            @Param("excludedIds") List<Long> excludedIds,
            @Param("excludedIdsEmpty") boolean excludedIdsEmpty
    );

    @Query("""
            select distinct lw
            from LiteratureWork lw
            left join fetch lw.genreMappings gm
            left join fetch gm.genre
            where lw.active = true
              and lw.id in :ids
            """)
    List<LiteratureWork> findActiveWorksByIds(@Param("ids") List<Long> ids);
}






