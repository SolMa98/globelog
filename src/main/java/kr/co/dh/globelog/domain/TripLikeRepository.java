package kr.co.dh.globelog.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripLikeRepository extends JpaRepository<TripLike, Long> {

    boolean existsByTripIdAndUserId(Long tripId, Long userId);

    long countByTripId(Long tripId);

    void deleteByTripIdAndUserId(Long tripId, Long userId);

    // 피드처럼 여러 trip을 한 번에 응답할 때 trip마다 countByTripId를 호출하는 N+1을
    // 피하기 위한 배치 조회 — trip당 좋아요 수를 한 번의 쿼리로 묶어서 가져온다.
    @Query("SELECT l.trip.id AS tripId, COUNT(l) AS cnt FROM TripLike l WHERE l.trip.id IN :tripIds GROUP BY l.trip.id")
    List<TripLikeCountProjection> countByTripIds(@Param("tripIds") List<Long> tripIds);

    // 위와 같은 이유로, 뷰어가 좋아요한 trip id 집합을 한 번에 가져와 existsByTripIdAndUserId를
    // trip마다 호출하지 않게 한다.
    @Query("SELECT l.trip.id FROM TripLike l WHERE l.trip.id IN :tripIds AND l.user.id = :userId")
    List<Long> findLikedTripIds(@Param("tripIds") List<Long> tripIds, @Param("userId") Long userId);

    // 게시글 상세(TripApiController.detail())용 — likeCount와 likedByViewer를 각각 쿼리
    // 두 번으로 나누는 대신, 좋아요한 사용자 id 목록을 한 번만 가져와 개수/포함여부를
    // 애플리케이션에서 같이 계산한다.
    @Query("SELECT l.user.id FROM TripLike l WHERE l.trip.id = :tripId")
    List<Long> findUserIdsByTripId(@Param("tripId") Long tripId);
}
