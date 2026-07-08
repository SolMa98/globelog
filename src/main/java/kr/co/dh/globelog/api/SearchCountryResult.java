package kr.co.dh.globelog.api;

import kr.co.dh.globelog.domain.Country;

public record SearchCountryResult(String isoA3, String nameKo, String nameEn) {

    public static SearchCountryResult from(Country country) {
        return new SearchCountryResult(country.getIsoA3(), country.getNameKo(), country.getNameEn());
    }
}
