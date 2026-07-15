package kr.co.dh.globelog.api;

import java.time.LocalDate;
import kr.co.dh.globelog.domain.Trip;

public record TripSummaryResponse(Long id, String title, LocalDate visitedDate, int visitNumber, long viewCount) {

    public static TripSummaryResponse from(Trip trip, int visitNumber) {
        return new TripSummaryResponse(trip.getId(), trip.getTitle(), trip.getVisitedDate(), visitNumber, trip.getViewCount());
    }
}