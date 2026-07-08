package kr.co.dh.globelog.admin;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * isoA3 -> 대륙 매핑. static/이 아니라 resources/data/에 두는 이유는
 * 브라우저가 직접 fetch할 필요 없는 서버 전용 참조 데이터이기 때문.
 */
@Service
public class ContinentMapService {

    private final Map<String, String> isoA3ToContinent;

    public ContinentMapService(
            @Value("classpath:data/continent-map.json") Resource resource,
            ObjectMapper objectMapper,
            CountryRefService countryRefService) {
        try {
            Map<String, String> raw = objectMapper.readValue(
                    resource.getInputStream(), new TypeReference<Map<String, String>>() {});
            // 데이터 오타/누락이 조용히 잘못된 통계로 이어지는 것을 막기 위해
            // countries-ref.json의 국가 코드 집합과 정확히 일치하는지 부팅 시점에 검증
            if (!raw.keySet().equals(countryRefService.getAllIsoA3Codes())) {
                throw new IllegalStateException(
                        "continent-map.json의 국가 코드 집합이 countries-ref.json과 일치하지 않습니다.");
            }
            this.isoA3ToContinent = Collections.unmodifiableMap(raw);
        } catch (IOException e) {
            throw new IllegalStateException("continent-map.json 로드 실패", e);
        }
    }

    public String getContinent(String isoA3) {
        return isoA3 == null ? null : isoA3ToContinent.get(isoA3.toUpperCase(Locale.ROOT));
    }
}
