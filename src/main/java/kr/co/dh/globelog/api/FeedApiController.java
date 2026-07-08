package kr.co.dh.globelog.api;

import java.util.List;
import kr.co.dh.globelog.domain.Trip;
import kr.co.dh.globelog.domain.TripImageRepository;
import kr.co.dh.globelog.domain.TripRepository;
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
    @GetMapping
    public List<FeedPostResponse> feed(
            @RequestParam(required = false) Integer limit,
            Authentication authentication) {
        int safeLimit = Math.min(Math.max(limit != null ? limit : DEFAULT_LIMIT, 1), MAX_LIMIT);
        List<Trip> trips = currentUserResolver.resolve(authentication)
                .map(viewer -> tripRepository.findRandomFeedForViewer(viewer.getId(), safeLimit))
                .orElseGet(() -> tripRepository.findRandomPublicFeed(safeLimit));
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
