package kr.co.dh.globelog.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.co.dh.globelog.domain.Country;
import kr.co.dh.globelog.domain.CountryRepository;
import kr.co.dh.globelog.domain.Trip;
import kr.co.dh.globelog.domain.TripRepository;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/countries")
public class CountryApiController {

    private final CountryRepository countryRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripVisibilityService tripVisibilityService;

    public CountryApiController(CountryRepository countryRepository, UserRepository userRepository,
            TripRepository tripRepository, TripVisibilityService tripVisibilityService) {
        this.countryRepository = countryRepository;
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
        this.tripVisibilityService = tripVisibilityService;
    }

    // 지구본은 반드시 특정 사용자(owner)의 것을 보는 구조 — 그 사람이 뷰어에게 보여줄
    // 수 있는(공개범위 통과) 여행이 있는 국가만 하이라이트한다.
    @GetMapping
    public List<CountrySummaryResponse> list(@RequestParam String owner, Authentication authentication) {
        User targetUser = findUserOrThrow(owner);
        Map<String, Country> visitedCountries = new LinkedHashMap<>();
        for (Trip trip : tripRepository.findByUserIdOrderByVisitedDateDesc(targetUser.getId())) {
            if (tripVisibilityService.canView(trip, authentication)) {
                visitedCountries.putIfAbsent(trip.getCountry().getIsoA3(), trip.getCountry());
            }
        }
        return visitedCountries.values().stream()
                .map(CountrySummaryResponse::from)
                .toList();
    }

    @GetMapping("/{isoA3}")
    public CountryDetailResponse detail(@PathVariable String isoA3) {
        Country country = countryRepository.findByIsoA3(isoA3.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "등록된 국가 정보가 없습니다: " + isoA3));
        return CountryDetailResponse.from(country);
    }

    private User findUserOrThrow(String nickname) {
        return userRepository.findByNickname(nickname)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다: " + nickname));
    }
}
