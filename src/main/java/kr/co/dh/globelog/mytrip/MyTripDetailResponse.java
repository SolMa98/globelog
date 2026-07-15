package kr.co.dh.globelog.mytrip;

import java.time.LocalDate;
import java.util.List;
import kr.co.dh.globelog.admin.AdminTripImageResponse;
import kr.co.dh.globelog.domain.Trip;
import kr.co.dh.globelog.domain.TripVisibility;

public record MyTripDetailResponse(
        Long id,
        Long regionId,
        String regionNameKo,
        Long countryId,
        String countryNameKo,
        String title,
        LocalDate visitedDate,
        LocalDate endDate,
        String description,
        TripVisibility visibility,
        long viewCount,
        List<AdminTripImageResponse> images) {

    public static MyTripDetailResponse from(Trip trip, List<AdminTripImageResponse> images) {
        Long regionId = trip.getRegion() != null ? trip.getRegion().getId() : null;
        String regionNameKo = trip.getRegion() != null ? trip.getRegion().getNameKo() : null;
        return new MyTripDetailResponse(
                trip.getId(), regionId, regionNameKo,
                trip.getCountry().getId(), trip.getCountry().getNameKo(),
                trip.getTitle(), trip.getVisitedDate(), trip.getEndDate(), trip.getDescription(),
                trip.getVisibility(), trip.getViewCount(), images);
    }
}
