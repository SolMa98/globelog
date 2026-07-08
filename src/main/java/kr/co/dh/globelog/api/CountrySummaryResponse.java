package kr.co.dh.globelog.api;

import kr.co.dh.globelog.domain.Country;

public record CountrySummaryResponse(String isoA3, String nameKo, String flagUrl) {

    public static CountrySummaryResponse from(Country country) {
        return new CountrySummaryResponse(country.getIsoA3(), country.getNameKo(), buildFlagUrl(country.getIsoA2()));
    }

    private static String buildFlagUrl(String isoA2) {
        if (isoA2 == null || isoA2.isBlank()) return null;
        return "https://flagcdn.com/w160/" + isoA2.toLowerCase() + ".png";
    }
}