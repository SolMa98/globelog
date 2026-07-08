package kr.co.dh.globelog.api;

import kr.co.dh.globelog.domain.Region;

public record SearchRegionResult(
        Long regionId,
        String nameKo,
        String nameEn,
        String countryIsoA3,
        String countryNameKo) {

    public static SearchRegionResult from(Region region) {
        return new SearchRegionResult(
                region.getId(), region.getNameKo(), region.getNameEn(),
                region.getCountry().getIsoA3(), region.getCountry().getNameKo());
    }
}
