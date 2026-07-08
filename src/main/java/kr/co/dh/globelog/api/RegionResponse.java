package kr.co.dh.globelog.api;

import kr.co.dh.globelog.domain.Region;

public record RegionResponse(
        Long id,
        String nameKo,
        String nameEn,
        String geojsonFeatureId,
        Double centerLat,
        Double centerLng) {

    public static RegionResponse from(Region region) {
        return new RegionResponse(
                region.getId(),
                region.getNameKo(),
                region.getNameEn(),
                region.getGeojsonFeatureId(),
                region.getCenterLat(),
                region.getCenterLng());
    }
}