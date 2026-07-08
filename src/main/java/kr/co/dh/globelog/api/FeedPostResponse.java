package kr.co.dh.globelog.api;

import java.time.LocalDate;
import kr.co.dh.globelog.domain.Trip;

public record FeedPostResponse(
        Long tripId,
        String title,
        LocalDate visitedDate,
        String coverImageUrl,
        Long authorId,
        String authorNickname,
        String authorProfileImageUrl,
        String regionNameKo,
        String countryIsoA3,
        String countryNameKo) {

    public static FeedPostResponse from(Trip trip, String coverImageUrl) {
        var user = trip.getUser();
        var region = trip.getRegion();
        var country = trip.getCountry();
        return new FeedPostResponse(
                trip.getId(),
                trip.getTitle(),
                trip.getVisitedDate(),
                coverImageUrl,
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                region != null ? region.getNameKo() : null,
                country.getIsoA3(),
                country.getNameKo());
    }
}
