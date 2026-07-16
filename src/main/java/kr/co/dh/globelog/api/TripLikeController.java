package kr.co.dh.globelog.api;

import kr.co.dh.globelog.domain.Trip;
import kr.co.dh.globelog.domain.TripLike;
import kr.co.dh.globelog.domain.TripLikeRepository;
import kr.co.dh.globelog.domain.TripRepository;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.security.CurrentUserResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 여행 게시글 좋아요 토글. Follow(ProfileController)와 동일한 소유권/인증 패턴 —
 * 로그인은 필수, 볼 수 없는(가시성 통과 못 하는) 게시글은 좋아요도 불가능하게 막는다.
 */
@RestController
@RequestMapping("/api/trips/{tripId}/like")
public class TripLikeController {

    private final TripRepository tripRepository;
    private final TripLikeRepository tripLikeRepository;
    private final TripVisibilityService tripVisibilityService;
    private final CurrentUserResolver currentUserResolver;

    public TripLikeController(TripRepository tripRepository, TripLikeRepository tripLikeRepository,
            TripVisibilityService tripVisibilityService, CurrentUserResolver currentUserResolver) {
        this.tripRepository = tripRepository;
        this.tripLikeRepository = tripLikeRepository;
        this.tripVisibilityService = tripVisibilityService;
        this.currentUserResolver = currentUserResolver;
    }

    // exists-check 후 save()라 동시 요청(더블탭, 여러 탭)이 겹치면 두 요청 모두 exists==false를
    // 보고 나서 각각 insert를 시도할 수 있다 — (trip_id, user_id) unique 제약을 위반하는
    // 두 번째 insert는 DataIntegrityViolationException으로 잡아 "이미 좋아요된 상태"와
    // 동일하게 취급한다(사용자 입장에선 최종 결과가 같으므로 500 대신 그대로 성공 처리).
    @PostMapping
    public TripLikeResponse like(@PathVariable Long tripId, Authentication authentication) {
        User viewer = requireLoggedIn(authentication);
        Trip trip = findViewableTripOrThrow(tripId, authentication);
        if (!tripLikeRepository.existsByTripIdAndUserId(trip.getId(), viewer.getId())) {
            try {
                tripLikeRepository.saveAndFlush(new TripLike(trip, viewer));
            } catch (DataIntegrityViolationException e) {
                // 동시 요청으로 이미 다른 트랜잭션이 먼저 넣은 경우 — 결과적으로 원하던 상태와 같음
            }
        }
        return new TripLikeResponse(tripLikeRepository.countByTripId(tripId), true);
    }

    @DeleteMapping
    @Transactional
    public TripLikeResponse unlike(@PathVariable Long tripId, Authentication authentication) {
        User viewer = requireLoggedIn(authentication);
        findViewableTripOrThrow(tripId, authentication);
        tripLikeRepository.deleteByTripIdAndUserId(tripId, viewer.getId());
        return new TripLikeResponse(tripLikeRepository.countByTripId(tripId), false);
    }

    private Trip findViewableTripOrThrow(Long tripId, Authentication authentication) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다: " + tripId));
        if (!tripVisibilityService.canView(trip, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "볼 수 없는 여행입니다.");
        }
        return trip;
    }

    private User requireLoggedIn(Authentication authentication) {
        return currentUserResolver.resolve(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }
}
