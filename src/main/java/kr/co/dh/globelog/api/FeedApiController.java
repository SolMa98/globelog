package kr.co.dh.globelog.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kr.co.dh.globelog.domain.Trip;
import kr.co.dh.globelog.domain.TripImageRepository;
import kr.co.dh.globelog.domain.TripLikeRepository;
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
    private final TripLikeRepository tripLikeRepository;
    private final CurrentUserResolver currentUserResolver;

    public FeedApiController(TripRepository tripRepository, TripImageRepository tripImageRepository,
            TripLikeRepository tripLikeRepository, CurrentUserResolver currentUserResolver) {
        this.tripRepository = tripRepository;
        this.tripImageRepository = tripImageRepository;
        this.tripLikeRepository = tripLikeRepository;
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
        Long viewerId = viewer.map(User::getId).orElse(null);

        // trip마다 좋아요 수/여부를 개별 쿼리하면 피드 한 번에 최대 MAX_LIMIT*2개의
        // 추가 쿼리가 나가므로(N+1), IN절로 한 번에 묶어서 가져온 뒤 메모리에서 매핑한다.
        // 빈 IN절은 일부 JPA 구현에서 예외가 나므로 trips가 비었으면 쿼리 자체를 건너뛴다.
        List<Long> tripIds = trips.stream().map(Trip::getId).toList();
        Map<Long, Long> likeCounts = new HashMap<>();
        Set<Long> likedTripIds = new HashSet<>();
        if (!tripIds.isEmpty()) {
            tripLikeRepository.countByTripIds(tripIds)
                    .forEach(row -> likeCounts.put(row.getTripId(), row.getCnt()));
            if (viewerId != null) {
                likedTripIds.addAll(tripLikeRepository.findLikedTripIds(tripIds, viewerId));
            }
        }

        return trips.stream()
                .map(trip -> FeedPostResponse.from(trip, coverImageUrl(trip),
                        likeCounts.getOrDefault(trip.getId(), 0L),
                        likedTripIds.contains(trip.getId())))
                .toList();
    }

    private String coverImageUrl(Trip trip) {
        return tripImageRepository.findFirstByTripIdOrderBySortOrderAsc(trip.getId())
                .map(image -> "/uploads/" + image.getFilePath())
                .orElse(null);
    }
}
