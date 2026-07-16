package kr.co.dh.globelog.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripCommentRepository extends JpaRepository<TripComment, Long> {

    List<TripComment> findByTripIdOrderByCreatedAtAsc(Long tripId);

    long countByTripId(Long tripId);
}
