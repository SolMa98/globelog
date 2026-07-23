package kr.co.dh.globelog.domain;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByRegionIdOrderByVisitedDateAsc(Long regionId);

    // 지역을 지정하지 않고 국가 단위로만 등록한 여행 — 스토리 보기(map.js)에서
    // 지역 마커가 아닌 "국가 전체" 진입점으로 묶어서 보여줄 때 씀.
    List<Trip> findByCountryIdAndRegionIsNullOrderByVisitedDateAsc(Long countryId);

    List<Trip> findByUserIdOrderByVisitedDateDesc(Long userId);

    // 프로필 페이지의 "여행 기록" 통계 카드용.
    long countByUserId(Long userId);

    // 피드 상단의 "방문 국가 수" 표시용 — 같은 국가에 여러 여행을 등록해도 1개국으로 센다.
    @Query("SELECT COUNT(DISTINCT t.country.id) FROM Trip t WHERE t.user.id = :userId")
    long countDistinctCountryByUserId(@Param("userId") Long userId);

    // 통계 화면(개인/관리자)의 "조회수" 카드용 — 삭제된 게시글의 조회수는 함께 사라짐
    // (등록/수정/삭제 건수는 SecurityEventLog로 별도 집계해 삭제 후에도 남지만, 조회수
    // 자체는 Trip.viewCount 필드가 유일한 출처라 현재 존재하는 게시글 기준으로만 합산됨).
    @Query("SELECT COALESCE(SUM(t.viewCount), 0) FROM Trip t")
    long sumViewCount();

    @Query("SELECT COALESCE(SUM(t.viewCount), 0) FROM Trip t WHERE t.user.id = :userId")
    long sumViewCountByUserId(@Param("userId") Long userId);

    Page<Trip> findByRegionId(Long regionId, Pageable pageable);

    Page<Trip> findByCountryId(Long countryId, Pageable pageable);

    @Query("SELECT t.country.isoA3 AS isoA3, t.visitedDate AS visitedDate FROM Trip t")
    List<TripStatsProjection> findStatsProjection();

    // 개인 통계 페이지(/my/stats)용 — 관리자 통계(findStatsProjection)는 전체 사용자
    // 합산이 의도된 동작이라 그대로 두고, 이건 완전히 분리된 메서드로 owner 하나만 본다.
    @Query("SELECT t.country.isoA3 AS isoA3, t.visitedDate AS visitedDate FROM Trip t WHERE t.user.id = :userId")
    List<TripStatsProjection> findStatsProjectionByUserId(@Param("userId") Long userId);

    // 공개 피드용 무작위 추출(비로그인 방문자용). "알고리즘"은 추천엔진이 아니라 단순
    // 랜덤 셔플에 관리자 우선순위 가중치만 얹은 것 — 완전 결정론적 고정노출이 아니라
    // POW(0.5, priority)로 정렬값을 작게 만들어(priority 1당 절반씩) LIMIT 안에 뽑힐
    // 확률을 높이는 방식. 순위를 확정 고정하면 "진짜 랜덤"이 아니게 되어 신뢰를 해칠
    // 수 있다는 게 이 방식을 고른 이유(project_multiuser_design_discussion 메모리 참고).
    // user_id가 없는(관리자 백오피스로 등록된 소유자 없는) 여행은 피드에서 "누구 글인지"
    // 알 수 없어 제외.
    @Query(value = "SELECT * FROM trip t WHERE t.visibility = 'PUBLIC' AND t.user_id IS NOT NULL "
            + "ORDER BY RAND() * POW(0.5, t.priority) LIMIT :limit", nativeQuery = true)
    List<Trip> findRandomPublicFeed(@Param("limit") int limit);

    // 로그인한 뷰어용 피드: 전체공개 + (친구공개이면서 뷰어가 글쓴이를 팔로우 중인 것).
    // "친구공개"는 양방향 친구가 아니라 일방향 팔로우(Follow) 기준.
    @Query(value = "SELECT * FROM trip t WHERE t.user_id IS NOT NULL AND ("
            + "t.visibility = 'PUBLIC' OR ("
            + "t.visibility = 'FOLLOWERS_ONLY' AND EXISTS ("
            + "SELECT 1 FROM follow f WHERE f.follower_id = :viewerId AND f.followee_id = t.user_id"
            + "))) ORDER BY RAND() * POW(0.5, t.priority) LIMIT :limit", nativeQuery = true)
    List<Trip> findRandomFeedForViewer(@Param("viewerId") Long viewerId, @Param("limit") int limit);

    // "팔로잉" 피드 탭: 뷰어가 팔로우한 사람의 글만, 최신순. 랜덤/가중치가 아니라
    // 사용자가 직접 고른 "이 사람들"만 보는 필터라 최신순이 더 직관적이라 판단.
    @Query(value = "SELECT * FROM trip t WHERE t.user_id IS NOT NULL "
            + "AND EXISTS (SELECT 1 FROM follow f WHERE f.follower_id = :viewerId AND f.followee_id = t.user_id) "
            + "AND (t.visibility = 'PUBLIC' OR t.visibility = 'FOLLOWERS_ONLY') "
            + "ORDER BY t.created_at DESC LIMIT :limit", nativeQuery = true)
    List<Trip> findFollowingFeed(@Param("viewerId") Long viewerId, @Param("limit") int limit);

    // "인기" 피드 탭(비로그인/공개용): 조회수 내림차순.
    @Query(value = "SELECT * FROM trip t WHERE t.visibility = 'PUBLIC' AND t.user_id IS NOT NULL "
            + "ORDER BY t.view_count DESC, t.created_at DESC LIMIT :limit", nativeQuery = true)
    List<Trip> findPopularPublicFeed(@Param("limit") int limit);

    // "인기" 피드 탭(로그인 뷰어용): 가시성 규칙은 기존 랜덤 피드와 동일, 정렬만 조회수 기준.
    @Query(value = "SELECT * FROM trip t WHERE t.user_id IS NOT NULL AND ("
            + "t.visibility = 'PUBLIC' OR ("
            + "t.visibility = 'FOLLOWERS_ONLY' AND EXISTS ("
            + "SELECT 1 FROM follow f WHERE f.follower_id = :viewerId AND f.followee_id = t.user_id"
            + "))) ORDER BY t.view_count DESC, t.created_at DESC LIMIT :limit", nativeQuery = true)
    List<Trip> findPopularFeedForViewer(@Param("viewerId") Long viewerId, @Param("limit") int limit);
}