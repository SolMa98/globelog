package kr.co.dh.globelog.admin;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kr.co.dh.globelog.domain.Country;
import kr.co.dh.globelog.domain.CountryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * countries-ref.json(전세계 236개국)에 있는데 country 테이블엔 없는 국가를 자동으로 채워
 * 넣는다 — "관리자가 하나씩 등록해야 여행을 남길 수 있는" 방식에서 "기본적으로 전부
 * 오픈, 관리자는 예외적으로 차단만" 방식으로 전환하기 위함(2026-07-07 확정).
 * 이미 있는 행은 절대 건드리지 않고 없는 것만 추가하므로 재부팅해도 안전(idempotent).
 *
 * RegionSeeder가 국가 존재를 전제로 하므로(country_id FK) @Order(1)로 먼저 실행되게 한다.
 */
@Component
@Order(1)
public class CountrySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CountrySeeder.class);

    private final CountryRepository countryRepository;
    private final CountryRefService countryRefService;

    public CountrySeeder(CountryRepository countryRepository, CountryRefService countryRefService) {
        this.countryRepository = countryRepository;
        this.countryRefService = countryRefService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> existingIsoA3 = countryRepository.findAll().stream()
                .map(Country::getIsoA3)
                .collect(Collectors.toSet());

        List<Country> toInsert = countryRefService.getAll().stream()
                .filter(entry -> !existingIsoA3.contains(entry.isoA3()))
                .map(entry -> new Country(entry.isoA3(), entry.isoA2(), entry.nameKo(), entry.nameEn(), null))
                .toList();

        if (!toInsert.isEmpty()) {
            countryRepository.saveAll(toInsert);
            log.info("국가 마스터 데이터 {}건을 countries-ref.json에서 자동 등록했습니다.", toInsert.size());
        }
    }
}
