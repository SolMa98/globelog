package kr.co.dh.globelog.api;

import kr.co.dh.globelog.domain.CountryImage;

public record CountryImageResponse(String url, int sortOrder) {

    public static CountryImageResponse from(CountryImage image) {
        return new CountryImageResponse("/uploads/" + image.getFilePath(), image.getSortOrder());
    }
}
