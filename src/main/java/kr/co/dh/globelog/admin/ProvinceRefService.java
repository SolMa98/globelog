package kr.co.dh.globelog.admin;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * provinces.json(국가 ISO A3 코드 → 시/도 목록) 로더. 원래는 관리자가 지역을 수동
 * 등록할 때 자동완성 후보(admin-regions.js가 /data/provinces.json을 직접 fetch)로만
 * 쓰였는데, RegionSeeder가 Region 마스터 데이터 자동 등록에도 그대로 재사용한다
 * (CountryRefService/CountrySeeder와 동일한 구조).
 */
@Service
public class ProvinceRefService {

    private final Map<String, List<ProvinceRefEntry>> byCountryIsoA3;

    public ProvinceRefService(
            @Value("classpath:static/data/provinces.json") Resource resource, ObjectMapper objectMapper) {
        try {
            Map<String, List<ProvinceRefEntry>> parsed = objectMapper.readValue(
                    resource.getInputStream(), new TypeReference<Map<String, List<ProvinceRefEntry>>>() {});
            this.byCountryIsoA3 = Collections.unmodifiableMap(parsed);
        } catch (IOException e) {
            throw new IllegalStateException("provinces.json 로드 실패", e);
        }
    }

    /** Region 마스터 데이터 자동 등록(RegionSeeder)에 쓰는 전체 목록 — 국가 ISO A3 코드별 시/도 배열. */
    public Map<String, List<ProvinceRefEntry>> getAll() {
        return byCountryIsoA3;
    }
}
