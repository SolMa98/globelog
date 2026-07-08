package kr.co.dh.globelog.admin;

/** countries-ref.json 한 행 — {@link CountrySeeder}가 Country 마스터 데이터 자동 등록에 그대로 사용. */
public record CountryRefEntry(String isoA3, String isoA2, String nameKo, String nameEn) {
}
