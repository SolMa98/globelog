package kr.co.dh.globelog.api;

import java.time.LocalDate;
import java.util.List;
import kr.co.dh.globelog.domain.Trip;

public record TripDetailResponse(
        Long id,
        String title,
        LocalDate visitedDate,
        String description,
        int visitNumber,
        List<TripImageResponse> images) {

    public static TripDetailResponse from(Trip trip, int visitNumber, List<TripImageResponse> images) {
        return new TripDetailResponse(
                trip.getId(),
                trip.getTitle(),
                trip.getVisitedDate(),
                trip.getDescription(),
                visitNumber,
                images);
    }
}