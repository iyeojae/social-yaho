package org.huss.socialsaas.interaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.huss.socialsaas.global.common.BaseTimeEntity;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.huss.socialsaas.user.entity.User;

@Getter
@Entity
@Table(name = "user_interaction_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInteractionEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "literature_work_id", nullable = false)
    private LiteratureWork literatureWork;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false, length = 30)
    private InteractionType interactionType;

    @Column(name = "view_duration_seconds")
    private Long viewDurationSeconds;

    @Column(name = "progress_percent")
    private Long progressPercent;

    @Column(name = "source_screen", length = 50)
    private String sourceScreen;

    @Builder
    private UserInteractionEvent(
            User user,
            LiteratureWork literatureWork,
            InteractionType interactionType,
            Long viewDurationSeconds,
            Long progressPercent,
            String sourceScreen
    ) {
        this.user = user;
        this.literatureWork = literatureWork;
        this.interactionType = interactionType;
        this.viewDurationSeconds = viewDurationSeconds;
        this.progressPercent = progressPercent;
        this.sourceScreen = sourceScreen;
    }

    public static UserInteractionEvent create(
            User user,
            LiteratureWork literatureWork,
            InteractionType interactionType,
            Long viewDurationSeconds,
            Long progressPercent,
            String sourceScreen
    ) {
        return UserInteractionEvent.builder()
                .user(user)
                .literatureWork(literatureWork)
                .interactionType(interactionType)
                .viewDurationSeconds(viewDurationSeconds)
                .progressPercent(progressPercent)
                .sourceScreen(sourceScreen)
                .build();
    }
}
