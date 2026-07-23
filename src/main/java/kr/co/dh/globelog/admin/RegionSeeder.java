package kr.co.dh.globelog.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.co.dh.globelog.domain.Country;
import kr.co.dh.globelog.domain.CountryRepository;
import kr.co.dh.globelog.domain.Region;
import kr.co.dh.globelog.domain.RegionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * provinces.json(전세계 251개국 시/도, 총 4,589건)에 있는데 region 테이블엔 없는 지역을
 * 자동으로 채워 넣는다 — CountrySeeder와 같은 방침: 국가뿐 아니라 지역도 기본적으로
 * 전부 등록해두고, 관리자는 필요할 때 특정 국가/지역만 예외적으로 차단(Region.enabled)
 * 하는 모델. 이미 있는 지역(같은 국가+이름)은 건드리지 않고 없는 것만 추가하므로
 * 재부팅해도 안전(idempotent) — Country가 먼저 시드돼 있어야 하므로 @Order로
 * CountrySeeder 다음에 실행되게 한다.
 */
@Component
@Order(2)
public class RegionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RegionSeeder.class);

    private final RegionRepository regionRepository;
    private final CountryRepository countryRepository;
    private final ProvinceRefService provinceRefService;

    public RegionSeeder(RegionRepository regionRepository, CountryRepository countryRepository,
            ProvinceRefService provinceRefService) {
        this.regionRepository = regionRepository;
        this.countryRepository = countryRepository;
        this.provinceRefService = provinceRefService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Region> toInsert = new ArrayList<>();

        for (Map.Entry<String, List<ProvinceRefEntry>> countryEntry : provinceRefService.getAll().entrySet()) {
            Country country = countryRepository.findByIsoA3(countryEntry.getKey()).orElse(null);
            if (country == null) {
                // countries-ref.json에 없는(=CountrySeeder가 등록 안 한) 국가 코드는 건너뜀 —
                // provinces.json 쪽 데이터 출처가 완전히 같은 국가 목록이 아닐 수 있어서 방어적으로 처리.
                continue;
            }

            Set<String> existingNames = regionRepository.findByCountryIdOrderByNameKoAsc(country.getId()).stream()
                    .map(Region::getNameKo)
                    .collect(Collectors.toSet());

            for (ProvinceRefEntry entry : countryEntry.getValue()) {
                if (existingNames.contains(entry.nameKo())) {
                    continue;
                }
                toInsert.add(new Region(country, entry.nameKo(), entry.nameEn(), null, entry.lat(), entry.lng()));
            }
        }

        if (!toInsert.isEmpty()) {
            regionRepository.saveAll(toInsert);
            log.info("지역 마스터 데이터 {}건을 provinces.json에서 자동 등록했습니다.", toInsert.size());
        }
    }
}
