package kr.co.dh.globelog.domain;

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
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 여행 게시글 좋아요. Follow와 동일하게 (trip_id, user_id) 유니크 제약으로 중복 좋아요를
 * 막고, 토글 여부는 exists 조회로 판정한다(카운터를 Trip에 직접 두지 않고 이 테이블을
 * 카운트하는 방식 — 누가 좋아요했는지/취소했는지 추적이 필요해 Follow 패턴을 따름).
 */
@Entity
@Table(name = "trip_like", uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "user_id"}))
public class TripLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TripLike() {
    }

    public TripLike(Trip trip, User user) {
        this.trip = trip;
        this.user = user;
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public User getUser() { return user; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
