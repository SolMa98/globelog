package kr.co.dh.globelog.api;

import java.util.ArrayList;
import java.util.List;
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
    private final TripRepository tripRepository;
    private final TripImageRepository tripImageRepository;
    private final UserRepository userRepository;
    private final TripVisibilityService tripVisibilityService;

    public TripApiController(RegionRepository regionRepository, TripRepository tripRepository,
            TripImageRepository tripImageRepository, UserRepository userRepository,
            TripVisibilityService tripVisibilityService) {
        this.regionRepository = regionRepository;
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

        List<TripSummaryResponse> result = new ArrayList<>();
        for (int i = 0; i < trips.size(); i++) {
            result.add(TripSummaryResponse.from(trips.get(i), i + 1));
        }
        return result;
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

        int visitNumber = 1;
        if (trip.getRegion() != null) {
            List<Trip> siblings = visibleTripsInRegion(targetUser.getId(), trip.getRegion().getId(), authentication);
            for (int i = 0; i < siblings.size(); i++) {
                if (siblings.get(i).getId().equals(id)) {
                    visitNumber = i + 1;
                    break;
                }
            }
        }

        List<TripImageResponse> images = tripImageRepository.findByTripIdOrderBySortOrderAsc(id)
                .stream()
                .map(TripImageResponse::from)
                .toList();

        return TripDetailResponse.from(trip, visitNumber, images);
    }

    private List<Trip> visibleTripsInRegion(Long ownerId, Long regionId, Authentication authentication) {
        return tripRepository.findByRegionIdOrderByVisitedDateAsc(regionId).stream()
                .filter(t -> t.getUser() != null && t.getUser().getId().equals(ownerId))
                .filter(t -> tripVisibilityService.canView(t, authentication))
                .toList();
    }

    private User findUserOrThrow(String nickname) {
        return userRepository.findByNickname(nickname)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다: " + nickname));
    }
}
