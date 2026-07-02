package org.huss.socialsaas.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.huss.socialsaas.global.common.BaseTimeEntity;
import org.huss.socialsaas.literature.entity.LiteratureWork;

import java.time.Instant;

@Getter
@Entity
@Table(name = "book_ai_tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookAiTag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "literature_work_id", nullable = false, unique = true)
    private LiteratureWork literatureWork;

    @Column(name = "llm_summary", columnDefinition = "TEXT")
    private String llmSummary;

    @Column(name = "recommendation_reason", columnDefinition = "TEXT")
    private String recommendationReason;

    @Column(name = "keyword_tags", columnDefinition = "TEXT")
    private String keywordTags;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Builder
    private BookAiTag(LiteratureWork literatureWork, String llmSummary, String recommendationReason, String keywordTags, Instant generatedAt) {
        this.literatureWork = literatureWork;
        this.llmSummary = llmSummary;
        this.recommendationReason = recommendationReason;
        this.keywordTags = keywordTags;
        this.generatedAt = generatedAt;
    }

    public static BookAiTag create(LiteratureWork literatureWork, String llmSummary, String recommendationReason, String keywordTags, Instant generatedAt) {
        return BookAiTag.builder()
                .literatureWork(literatureWork)
                .llmSummary(llmSummary)
                .recommendationReason(recommendationReason)
                .keywordTags(keywordTags)
                .generatedAt(generatedAt)
                .build();
    }
}

