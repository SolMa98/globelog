package kr.co.dh.globelog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "trip")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 여행 소유자. 다중 사용자 전환 이전 데이터(관리자 백오피스로 등록)는 소유자가 없어
    // nullable로 둠 — 기존 22건짜리 데이터를 깨지 않기 위한 절충. 신규 셀프가입 사용자의
    // 여행은 항상 채워짐.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 지역이 있는 여행
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    // 지역 없이 국가 전체를 대상으로 하는 여행 또는 지역 소속 확인용
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "visited_date", nullable = false)
    private LocalDate visitedDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Lob
    @Column(name = "description")
    private String description;

    // 공개 피드 노출 여부. 다중 사용자 전환 전 기존 데이터는 전부 PUBLIC이 자연스러운
    // 기본값(이 앱 자체가 처음부터 완전 공개였음).
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private TripVisibility visibility = TripVisibility.PUBLIC;

    // 피드 노출 우선순위(관리자 전용 설정, 글쓴이 본인도 못 바꿈) — 0이 기본, 높을수록
    // 랜덤 피드에서 더 자주/앞쪽에 노출되도록 가중치를 줌(완전 결정론적 고정 노출은 아님).
    @Column(name = "priority", nullable = false)
    private int priority = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Trip() {
    }

    public Trip(User user, Region region, Country country, String title, LocalDate visitedDate, LocalDate endDate, String description) {
        this.user = user;
        this.region = region;
        this.country = country;
        this.title = title;
        this.visitedDate = visitedDate;
        this.endDate = endDate;
        this.description = description;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Region getRegion() { return region; }
    public Country getCountry() { return country; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getVisitedDate() { return visitedDate; }
    public void setVisitedDate(LocalDate visitedDate) { this.visitedDate = visitedDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TripVisibility getVisibility() { return visibility; }
    public void setVisibility(TripVisibility visibility) { this.visibility = visibility; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}