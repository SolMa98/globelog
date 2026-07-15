package kr.co.dh.globelog.api;

import java.util.List;
import java.util.Optional;
import kr.co.dh.globelog.domain.Trip;
import kr.co.dh.globelog.domain.TripImageRepository;
import kr.co.dh.globelog.domain.TripRepository;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.security.CurrentUserResolver;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
public class FeedApiController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final TripRepository tripRepository;
    private final TripImageRepository tripImageRepository;
    private final CurrentUserResolver currentUserResolver;

    public FeedApiController(TripRepository tripRepository, TripImageRepository tripImageRepository,
            CurrentUserResolver currentUserResolver) {
        this.tripRepository = tripRepository;
        this.tripImageRepository = tripImageRepository;
        this.currentUserResolver = currentUserResolver;
    }

    // 뷰어는 클라이언트가 보낸 값이 아니라 항상 세션(SecurityContext)에서만 조회한다 —
    // 이전엔 viewerId를 요청 파라미터로 받는 임시 방편을 썼는데(스푸핑 가능), 로그인이
    // 붙었으니 제거함(project_multiuser_design_discussion 메모리 참고).
    // filter: "all"(기본, 우선순위 가중 랜덤) · "following"(내가 팔로우한 사람 글만,
    // 최신순 — 비로그인이면 빈 목록) · "popular"(조회수 내림차순).
    @GetMapping
    public List<FeedPostResponse> feed(
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "all") String filter,
            Authentication authentication) {
        int safeLimit = Math.min(Math.max(limit != null ? limit : DEFAULT_LIMIT, 1), MAX_LIMIT);
        Optional<User> viewer = currentUserResolver.resolve(authentication);
        List<Trip> trips = switch (filter) {
            case "following" -> viewer
                    .map(v -> tripRepository.findFollowingFeed(v.getId(), safeLimit))
                    .orElseGet(List::of);
            case "popular" -> viewer
                    .map(v -> tripRepository.findPopularFeedForViewer(v.getId(), safeLimit))
                    .orElseGet(() -> tripRepository.findPopularPublicFeed(safeLimit));
            default -> viewer
                    .map(v -> tripRepository.findRandomFeedForViewer(v.getId(), safeLimit))
                    .orElseGet(() -> tripRepository.findRandomPublicFeed(safeLimit));
        };
        return trips.stream()
                .map(trip -> FeedPostResponse.from(trip, coverImageUrl(trip)))
                .toList();
    }

    private String coverImageUrl(Trip trip) {
        return tripImageRepository.findFirstByTripIdOrderBySortOrderAsc(trip.getId())
                .map(image -> "/uploads/" + image.getFilePath())
                .orElse(null);
    }
}
