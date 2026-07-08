package kr.co.dh.globelog.admin;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class CountryRefService {

    private final List<CountryRefEntry> entries;
    private final Set<String> validIsoA3Codes;

    public CountryRefService(
            @Value("classpath:static/data/countries-ref.json") Resource resource, ObjectMapper objectMapper) {
        try {
            List<CountryRefEntry> parsed = objectMapper.readValue(
                    resource.getInputStream(), new TypeReference<List<CountryRefEntry>>() {});
            this.entries = Collections.unmodifiableList(parsed);
            this.validIsoA3Codes = Collections.unmodifiableSet(
                    parsed.stream()
                            .map(e -> e.isoA3().toUpperCase(Locale.ROOT))
                            .collect(Collectors.toSet()));
        } catch (IOException e) {
            throw new IllegalStateException("countries-ref.json 로드 실패", e);
        }
    }

    public boolean isValidIsoA3(String isoA3) {
        if (isoA3 == null || isoA3.isBlank()) return false;
        return validIsoA3Codes.contains(isoA3.toUpperCase(Locale.ROOT));
    }

    public Set<String> getAllIsoA3Codes() {
        return validIsoA3Codes;
    }

    /** Country 마스터 데이터 자동 등록(CountrySeeder)에 쓰는 전체 목록. */
    public List<CountryRefEntry> getAll() {
        return entries;
    }
}
