package org.huss.socialsaas.preference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.huss.socialsaas.global.common.BaseTimeEntity;
import org.huss.socialsaas.literature.entity.Genre;
import org.huss.socialsaas.user.entity.User;

@Getter
@Entity
@Table(
        name = "user_genre_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_genre_preference", columnNames = {"user_id", "genre_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserGenrePreference extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "genre_id", nullable = false)
    private Genre genre;

    @Column(name = "explicit_score", nullable = false)
    private long explicitScore;

    @Column(name = "implicit_score", nullable = false)
    private long implicitScore;

    @Column(name = "total_score", nullable = false)
    private long totalScore;

    @Builder
    private UserGenrePreference(User user, Genre genre, long explicitScore, long implicitScore, long totalScore) {
        this.user = user;
        this.genre = genre;
        this.explicitScore = explicitScore;
        this.implicitScore = implicitScore;
        this.totalScore = totalScore;
    }

    public static UserGenrePreference create(User user, Genre genre) {
        return UserGenrePreference.builder()
                .user(user)
                .genre(genre)
                .explicitScore(0L)
                .implicitScore(0L)
                .totalScore(0L)
                .build();
    }

    public static UserGenrePreference create(User user, Genre genre, long explicitScore, long implicitScore) {
        return UserGenrePreference.builder()
                .user(user)
                .genre(genre)
                .explicitScore(explicitScore)
                .implicitScore(implicitScore)
                .totalScore(explicitScore + implicitScore)
                .build();
    }

    public void addExplicitScore(long score) {
        this.explicitScore += score;
        recalculateTotalScore();
    }

    public void addImplicitScore(long score) {
        this.implicitScore += score;
        recalculateTotalScore();
    }

    public void recalculateTotalScore() {
        this.totalScore = this.explicitScore + this.implicitScore;
    }
}
