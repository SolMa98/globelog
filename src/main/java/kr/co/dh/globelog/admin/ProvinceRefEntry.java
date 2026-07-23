package kr.co.dh.globelog.admin;

/** provinces.json의 국가별 배열 안 항목 하나 — {@link RegionSeeder}가 Region 마스터 데이터 자동 등록에 그대로 사용. */
public record ProvinceRefEntry(String nameKo, String nameEn, Double lat, Double lng) {
}
