package kr.co.dh.globelog.api;

import kr.co.dh.globelog.domain.Region;
import kr.co.dh.globelog.domain.Trip;

public record SearchTripResult(
        Long tripId,
        String title,
        Long regionId,
        String regionNameKo,
        String countryIsoA3,
        String countryNameKo) {

    public static SearchTripResult from(Trip trip) {
        Region region = trip.getRegion();
        return new SearchTripResult(
                trip.getId(), trip.getTitle(),
                region != null ? region.getId() : null,
                region != null ? region.getNameKo() : null,
                trip.getCountry().getIsoA3(), trip.getCountry().getNameKo());
    }
}
