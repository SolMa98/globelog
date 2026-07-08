package kr.co.dh.globelog.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.co.dh.globelog.domain.Country;
import kr.co.dh.globelog.domain.CountryRepository;
import kr.co.dh.globelog.domain.Region;
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
public class RegionApiController {

    private final CountryRepository countryRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripVisibilityService tripVisibilityService;

    public RegionApiController(CountryRepository countryRepository, UserRepository userRepository,
            TripRepository tripRepository, TripVisibilityService tripVisibilityService) {
        this.countryRepository = countryRepository;
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
        this.tripVisibilityService = tripVisibilityService;
    }

    // owner가 이 국가 안에서 뷰어에게 보여줄 수 있는 여행을 남긴 지역만 마커로 노출.
    // 지역 자체는 Region 테이블에 다 있지만(다른 사용자와 공유하는 참조 데이터),
    // 이 owner가 실제로 방문 기록을 안 남긴 지역까지 보여주면 클릭해도 빈 스토리만
    // 뜨는 이상한 UX가 되므로 여기서 걸러낸다.
    @GetMapping("/{isoA3}/regions")
    public List<RegionResponse> regions(
            @PathVariable String isoA3, @RequestParam String owner, Authentication authentication) {
        Country country = countryRepository.findByIsoA3(isoA3.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "등록된 국가 정보가 없습니다: " + isoA3));
        User targetUser = findUserOrThrow(owner);

        Map<Long, Region> visitedRegions = new LinkedHashMap<>();
        for (Trip trip : tripRepository.findByUserIdOrderByVisitedDateDesc(targetUser.getId())) {
            if (trip.getRegion() == null || !trip.getCountry().getId().equals(country.getId())) {
                continue;
            }
            if (tripVisibilityService.canView(trip, authentication)) {
                visitedRegions.putIfAbsent(trip.getRegion().getId(), trip.getRegion());
            }
        }
        return visitedRegions.values().stream()
                .map(RegionResponse::from)
                .toList();
    }

    private User findUserOrThrow(String nickname) {
        return userRepository.findByNickname(nickname)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다: " + nickname));
    }
}
