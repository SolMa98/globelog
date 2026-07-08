package kr.co.dh.globelog.admin;

import kr.co.dh.globelog.domain.Region;

public record AdminRegionDetailResponse(
        Long id,
        Long countryId,
        String countryNameKo,
        String countryIsoA3,
        String nameKo,
        String nameEn,
        String geojsonFeatureId,
        Double centerLat,
        Double centerLng,
        boolean enabled) {

    public static AdminRegionDetailResponse from(Region region) {
        return new AdminRegionDetailResponse(
                region.getId(),
                region.getCountry().getId(),
                region.getCountry().getNameKo(),
                region.getCountry().getIsoA3(),
                region.getNameKo(),
                region.getNameEn(),
                region.getGeojsonFeatureId(),
                region.getCenterLat(),
                region.getCenterLng(),
                region.isEnabled());
    }
}