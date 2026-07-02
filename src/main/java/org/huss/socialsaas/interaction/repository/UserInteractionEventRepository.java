package org.huss.socialsaas.interaction.repository;

import org.huss.socialsaas.interaction.entity.UserInteractionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserInteractionEventRepository extends JpaRepository<UserInteractionEvent, Long> {

	@Query(value = """
			select recent.literature_work_id
			from (
				select uie.literature_work_id, max(uie.created_at) as last_interacted_at
				from user_interaction_events uie
				where uie.user_id = :userId
				group by uie.literature_work_id
				order by last_interacted_at desc
				limit :limit
			) recent
			order by recent.last_interacted_at desc
			""", nativeQuery = true)
	List<Long> findRecentDistinctBookIds(@Param("userId") Long userId, @Param("limit") int limit);
}

