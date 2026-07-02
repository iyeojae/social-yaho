package org.huss.socialsaas.literature.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.huss.socialsaas.global.common.BaseTimeEntity;

@Getter
@Entity
@Table(
        name = "literature_work_genres",
        uniqueConstraints = @UniqueConstraint(name = "uk_work_genre", columnNames = {"literature_work_id", "genre_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiteratureWorkGenre extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "literature_work_id", nullable = false)
    private LiteratureWork literatureWork;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "genre_id", nullable = false)
    private Genre genre;

    public LiteratureWorkGenre(LiteratureWork literatureWork, Genre genre) {
        this.literatureWork = literatureWork;
        this.genre = genre;
    }
}
