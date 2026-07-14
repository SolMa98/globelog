package kr.co.dh.globelog.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kr.co.dh.globelog.domain.Country;
import kr.co.dh.globelog.domain.CountryRepository;
import kr.co.dh.globelog.domain.RegionRepository;
import kr.co.dh.globelog.domain.Trip;
import kr.co.dh.globelog.domain.TripImageRepository;
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
@RequestMapping("/api")
public class TripApiController {

    private final RegionRepository regionRepository;
    private final CountryRepository countryRepository;
    private final TripRepository tripRepository;
    private final TripImageRepository tripImageRepository;
    private final UserRepository userRepository;
    private final TripVisibilityService tripVisibilityService;

    public TripApiController(RegionRepository regionRepository, CountryRepository countryRepository,
            TripRepository tripRepository, TripImageRepository tripImageRepository, UserRepository userRepository,
            TripVisibilityService tripVisibilityService) {
        this.regionRepository = regionRepository;
        this.countryRepository = countryRepository;
        this.tripRepository = tripRepository;
        this.tripImageRepository = tripImageRepository;
        this.userRepository = userRepository;
        this.tripVisibilityService = tripVisibilityService;
    }

    // owner 소유이면서 뷰어에게 공개범위가 통과되는 여행만, 그 owner 기준 방문 순번을
    // 매겨서 반환한다(예전엔 지역 전체 trip 기준이라 여러 사용자가 섞였음).
    @GetMapping("/regions/{regionId}/trips")
    public List<TripSummaryResponse> trips(
            @PathVariable Long regionId, @RequestParam String owner, Authentication authentication) {
        if (!regionRepository.existsById(regionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "지역을 찾을 수 없습니다: " + regionId);
        }
        User targetUser = findUserOrThrow(owner);
        List<Trip> trips = visibleTripsInRegion(targetUser.getId(), regionId, authentication);
        return numbered(trips);
    }

    // 지역을 지정하지 않고 국가 단위로만 등록한 여행 — map.js의 "지역 미지정 여행" 진입점이
    // /api/regions/{id}/trips와 같은 모양(TripSummaryResponse 목록)으로 소비할 수 있게 함.
    @GetMapping("/countries/{isoA3}/trips")
    public List<TripSummaryResponse> countryTrips(
            @PathVariable String isoA3, @RequestParam String owner, Authentication authentication) {
        Country country = countryRepository.findByIsoA3(isoA3.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "등록된 국가 정보가 없습니다: " + isoA3));
        User targetUser = findUserOrThrow(owner);
        List<Trip> trips = visibleTripsInCountryWithoutRegion(targetUser.getId(), country.getId(), authentication);
        return numbered(trips);
    }

    @GetMapping("/trips/{id}")
    public TripDetailResponse detail(@PathVariable Long id, @RequestParam String owner, Authentication authentication) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다: " + id));
        User targetUser = findUserOrThrow(owner);
        if (trip.getUser() == null || !trip.getUser().getId().equals(targetUser.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다: " + id);
        }
        if (!tripVisibilityService.canView(trip, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "볼 수 없는 여행입니다.");
        }

        List<Trip> siblings = trip.getRegion() != null
                ? visibleTripsInRegion(targetUser.getId(), trip.getRegion().getId(), authentication)
                : visibleTripsInCountryWithoutRegion(targetUser.getId(), trip.getCountry().getId(), authentication);
        int visitNumber = 1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getId().equals(id)) {
                visitNumber = i + 1;
                break;
            }
        }

        List<TripImageResponse> images = tripImageRepository.findByTripIdOrderBySortOrderAsc(id)
                .stream()
                .map(TripImageResponse::from)
                .toList();

        Long regionId = trip.getRegion() != null ? trip.getRegion().getId() : null;
        return TripDetailResponse.from(trip, regionId, visitNumber, images);
    }

    private List<TripSummaryResponse> numbered(List<Trip> trips) {
        List<TripSummaryResponse> result = new ArrayList<>();
        for (int i = 0; i < trips.size(); i++) {
            result.add(TripSummaryResponse.from(trips.get(i), i + 1));
        }
        return result;
    }

    private List<Trip> visibleTripsInRegion(Long ownerId, Long regionId, Authentication authentication) {
        return tripRepository.findByRegionIdOrderByVisitedDateAsc(regionId).stream()
                .filter(t -> t.getUser() != null && t.getUser().getId().equals(ownerId))
                .filter(t -> tripVisibilityService.canView(t, authentication))
                .toList();
    }

    private List<Trip> visibleTripsInCountryWithoutRegion(Long ownerId, Long countryId, Authentication authentication) {
        return tripRepository.findByCountryIdAndRegionIsNullOrderByVisitedDateAsc(countryId).stream()
                .filter(t -> t.getUser() != null && t.getUser().getId().equals(ownerId))
                .filter(t -> tripVisibilityService.canView(t, authentication))
                .toList();
    }

    private User findUserOrThrow(String nickname) {
        return userRepository.findByNickname(nickname)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다: " + nickname));
    }
}
