package kr.co.dh.globelog.api;

import java.time.LocalDate;
import java.util.List;
import kr.co.dh.globelog.domain.Trip;

public record TripDetailResponse(
        Long id,
        Long regionId,
        String title,
        LocalDate visitedDate,
        String description,
        int visitNumber,
        List<TripImageResponse> images) {

    public static TripDetailResponse from(Trip trip, Long regionId, int visitNumber, List<TripImageResponse> images) {
        return new TripDetailResponse(
                trip.getId(),
                regionId,
                trip.getTitle(),
                trip.getVisitedDate(),
                trip.getDescription(),
                visitNumber,
                images);
    }
}