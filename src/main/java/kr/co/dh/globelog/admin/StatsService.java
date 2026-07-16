package kr.co.dh.globelog.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.co.dh.globelog.domain.TripRepository;
import kr.co.dh.globelog.domain.TripStatsProjection;
import org.springframework.stereotype.Service;

/**
 * 통계 계산 — 관리자 백오피스(/admin/stats, compute())와 개인 통계 페이지
 * (/my/stats, computeForUser())가 함께 쓴다.
 *
 * {@link TripRepository#findStatsProjection()}는 owner/visibility 필터가 전혀 없이
 * 전체 사용자의 모든 여행을 합산한다 — 단일 사용자 시절에 만들어진 코드가 다중 사용자
 * 전환 이후에도 그대로 남은 것이 아니라, "관리자 감사용 대시보드는 전체 사용자 합산
 * (비공개 포함)"으로 의도적으로 유지하기로 확정한 것이다(2026-07-07). computeForUser()는
 * 그 결정을 건드리지 않기 위해 조건부 파라미터를 끼워넣지 않고 완전히 분리해서 추가함.
 */
@Service
public class StatsService {

    private static final List<String> CONTINENT_ORDER = List.of(
            "ASIA", "EUROPE", "AFRICA", "NORTH_AMERICA", "SOUTH_AMERICA", "OCEANIA", "ANTARCTICA");

    private final TripRepository tripRepository;
    private final CountryRefService countryRefService;
    private final ContinentMapService continentMapService;

    public StatsService(TripRepository tripRepository, CountryRefService countryRefService,
            ContinentMapService continentMapService) {
        this.tripRepository = tripRepository;
        this.countryRefService = countryRefService;
        this.continentMapService = continentMapService;
    }

    public AdminStatsResponse compute() {
        return computeFrom(tripRepository.findStatsProjection());
    }

    // 개인 통계(/my/stats)용 — 클래스 상단 주석대로 조건부 파라미터를 끼워넣지 않고
    // 완전히 분리된 공개 메서드로 둔다. 집계 로직 자체는 compute()와 동일해 computeFrom으로만 공유.
    public AdminStatsResponse computeForUser(Long userId) {
        return computeFrom(tripRepository.findStatsProjectionByUserId(userId));
    }

    private AdminStatsResponse computeFrom(List<TripStatsProjection> trips) {
        Set<String> visitedIsoA3 = trips.stream()
                .map(TripStatsProjection::getIsoA3)
                .collect(Collectors.toSet());

        Set<String> allIsoA3 = countryRefService.getAllIsoA3Codes();
        int totalCountryCount = allIsoA3.size();
        int visitedCountryCount = visitedIsoA3.size();

        Map<String, long[]> tally = new LinkedHashMap<>(); // continent -> [visited, total]
        CONTINENT_ORDER.forEach(c -> tally.put(c, new long[2]));
        for (String iso : allIsoA3) {
            String continent = continentMapService.getContinent(iso);
            if (tally.containsKey(continent)) {
                tally.get(continent)[1]++;
            }
        }
        for (String iso : visitedIsoA3) {
            String continent = continentMapService.getContinent(iso);
            if (tally.containsKey(continent)) {
                tally.get(continent)[0]++;
            }
        }
        List<ContinentCoverage> continents = CONTINENT_ORDER.stream()
                .map(c -> new ContinentCoverage(c, tally.get(c)[0], tally.get(c)[1]))
                .toList();

        Map<Integer, Long> yearCounts = trips.stream()
                .collect(Collectors.groupingBy(t -> t.getVisitedDate().getYear(), Collectors.counting()));
        List<YearlyTripCount> yearly = yearCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new YearlyTripCount(e.getKey(), e.getValue()))
                .toList();

        return new AdminStatsResponse(visitedCountryCount, totalCountryCount, continents, yearly);
    }
}
