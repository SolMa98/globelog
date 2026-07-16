package kr.co.dh.globelog.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripLikeRepository extends JpaRepository<TripLike, Long> {

    boolean existsByTripIdAndUserId(Long tripId, Long userId);

    long countByTripId(Long tripId);

    void deleteByTripIdAndUserId(Long tripId, Long userId);
}
