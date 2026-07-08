package kr.co.dh.globelog.admin;

import kr.co.dh.globelog.domain.Country;

public record AdminCountryDetailResponse(
        Long id,
        String isoA3,
        String isoA2,
        String nameKo,
        String nameEn,
        String description,
        String flagUrl,
        boolean enabled) {

    public static AdminCountryDetailResponse from(Country country) {
        String isoA2 = country.getIsoA2();
        String flagUrl = (isoA2 == null || isoA2.isBlank()) ? null
                : "https://flagcdn.com/w160/" + isoA2.toLowerCase() + ".png";
        return new AdminCountryDetailResponse(
                country.getId(), country.getIsoA3(), isoA2,
                country.getNameKo(), country.getNameEn(), country.getDescription(), flagUrl,
                country.isEnabled());
    }
}