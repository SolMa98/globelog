package kr.co.dh.globelog.api;

import kr.co.dh.globelog.domain.TripImage;

public record TripImageResponse(Long id, String url, int sortOrder) {

    public static TripImageResponse from(TripImage image) {
        return new TripImageResponse(image.getId(), "/uploads/" + image.getFilePath(), image.getSortOrder());
    }
}