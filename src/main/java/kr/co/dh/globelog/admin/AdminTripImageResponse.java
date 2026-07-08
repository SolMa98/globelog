package kr.co.dh.globelog.admin;

import kr.co.dh.globelog.domain.TripImage;

public record AdminTripImageResponse(Long id, String url, String originalFilename) {

    public static AdminTripImageResponse from(TripImage image) {
        return new AdminTripImageResponse(image.getId(), "/uploads/" + image.getFilePath(), image.getOriginalFilename());
    }
}