package kr.co.dh.globelog.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.co.dh.globelog.domain.Country;
import kr.co.dh.globelog.domain.Region;
import kr.co.dh.globelog.domain.Trip;
import kr.co.dh.globelog.domain.TripRepository;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// 검색도 지구본과 마찬가지로 항상 특정 owner 스코프 안에서만 이뤄진다 — 남의 지구본을
// 보다가 검색해도 그 사람이 뷰어에게 보여줄 수 있는 데이터만 나와야 하기 때문
// (project_multiuser_design_discussion 메모리 참고).
@RestController
@RequestMapping("/api/search")
public class SearchApiController {

    private static final int MAX_RESULTS_PER_TYPE = 8;

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TripVisibilityService tripVisibilityService;

    public SearchApiController(TripRepository tripRepository, UserRepository userRepository,
            TripVisibilityService tripVisibilityService) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.tripVisibilityService = tripVisibilityService;
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam String owner,
            Authentication authentication) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return new SearchResponse(List.of(), List.of(), List.of());
        }

        User targetUser = userRepository.findByNickname(owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다: " + owner));

        Map<String, Country> matchedCountries = new LinkedHashMap<>();
        Map<Long, Region> matchedRegions = new LinkedHashMap<>();
        List<SearchTripResult> trips = new ArrayList<>();

        for (Trip trip : tripRepository.findByUserIdOrderByVisitedDateDesc(targetUser.getId())) {
            if (!tripVisibilityService.canView(trip, authentication)) {
                continue;
            }
            Country country = trip.getCountry();
            if (matches(query, country.getNameKo(), country.getNameEn())) {
                matchedCountries.putIfAbsent(country.getIsoA3(), country);
            }
            Region region = trip.getRegion();
            if (region != null && matches(query, region.getNameKo(), region.getNameEn())) {
                matchedRegions.putIfAbsent(region.getId(), region);
            }
            if (trips.size() < MAX_RESULTS_PER_TYPE && trip.getTitle() != null
                    && trip.getTitle().toLowerCase(Locale.ROOT).contains(query)) {
                trips.add(SearchTripResult.from(trip));
            }
        }

        List<SearchCountryResult> countries = matchedCountries.values().stream()
                .limit(MAX_RESULTS_PER_TYPE)
                .map(SearchCountryResult::from)
                .toList();
        List<SearchRegionResult> regions = matchedRegions.values().stream()
                .limit(MAX_RESULTS_PER_TYPE)
                .map(SearchRegionResult::from)
                .toList();

        return new SearchResponse(countries, regions, trips);
    }

    private boolean matches(String query, String nameKo, String nameEn) {
        return (nameKo != null && nameKo.toLowerCase(Locale.ROOT).contains(query))
                || (nameEn != null && nameEn.toLowerCase(Locale.ROOT).contains(query));
    }
}
