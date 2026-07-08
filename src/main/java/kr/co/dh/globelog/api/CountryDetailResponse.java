package kr.co.dh.globelog.api;

import kr.co.dh.globelog.domain.Country;

public record CountryDetailResponse(
        String isoA3,
        String isoA2,
        String nameKo,
        String nameEn,
        String description,
        String flagUrl) {

    public static CountryDetailResponse from(Country country) {
        return new CountryDetailResponse(
                country.getIsoA3(),
                country.getIsoA2(),
                country.getNameKo(),
                country.getNameEn(),
                country.getDescription(),
                buildFlagUrl(country.getIsoA2()));
    }

    private static String buildFlagUrl(String isoA2) {
        if (isoA2 == null || isoA2.isBlank()) return null;
        return "https://flagcdn.com/w160/" + isoA2.toLowerCase() + ".png";
    }
}