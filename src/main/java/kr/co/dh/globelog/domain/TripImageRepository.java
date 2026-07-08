package kr.co.dh.globelog.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripImageRepository extends JpaRepository<TripImage, Long> {

    List<TripImage> findByTripOrderBySortOrderAsc(Trip trip);

    List<TripImage> findByTripIdOrderBySortOrderAsc(Long tripId);

    Optional<TripImage> findFirstByTripIdOrderBySortOrderAsc(Long tripId);
}