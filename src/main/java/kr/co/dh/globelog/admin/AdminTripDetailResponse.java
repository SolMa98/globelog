package kr.co.dh.globelog.admin;

import java.time.LocalDate;
import java.util.List;
import kr.co.dh.globelog.domain.Trip;

public record AdminTripDetailResponse(
        Long id,
        Long regionId,
        String regionNameKo,
        Long countryId,
        String countryNameKo,
        String title,
        LocalDate visitedDate,
        LocalDate endDate,
        String description,
        int priority,
        List<AdminTripImageResponse> images) {

    public static AdminTripDetailResponse from(Trip trip, List<AdminTripImageResponse> images) {
        Long regionId = trip.getRegion() != null ? trip.getRegion().getId() : null;
        String regionNameKo = trip.getRegion() != null ? trip.getRegion().getNameKo() : null;
        return new AdminTripDetailResponse(
                trip.getId(), regionId, regionNameKo,
                trip.getCountry().getId(), trip.getCountry().getNameKo(),
                trip.getTitle(), trip.getVisitedDate(), trip.getEndDate(), trip.getDescription(),
                trip.getPriority(), images);
    }
}
