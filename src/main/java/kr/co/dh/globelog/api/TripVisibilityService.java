package kr.co.dh.globelog.api;

import java.util.Optional;
import kr.co.dh.globelog.domain.FollowRepository;
import kr.co.dh.globelog.domain.Trip;
import kr.co.dh.globelog.domain.TripVisibility;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.security.CurrentUserResolver;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * 사용자별로 스코프된 지구본/지도 API(country/region/trip)가 공통으로 쓰는 가시성
 * 판정. 본인 글은 공개범위 무관하게 항상 보이고, 남의 글은 PUBLIC이거나
 * (FOLLOWERS_ONLY이면서 뷰어가 글쓴이를 팔로우 중)일 때만 보인다.
 */
@Service
public class TripVisibilityService {

    private final FollowRepository followRepository;
    private final CurrentUserResolver currentUserResolver;

    public TripVisibilityService(FollowRepository followRepository, CurrentUserResolver currentUserResolver) {
        this.followRepository = followRepository;
        this.currentUserResolver = currentUserResolver;
    }

    public boolean canView(Trip trip, Authentication authentication) {
        if (trip.getUser() == null) {
            return false; // 소유자 없는 레거시 데이터는 사용자별 스코프 지구본에는 노출 안 함
        }
        Optional<User> viewer = currentUserResolver.resolve(authentication);
        if (viewer.isPresent() && viewer.get().getId().equals(trip.getUser().getId())) {
            return true; // 본인 글은 항상 보임
        }
        if (trip.getVisibility() == TripVisibility.PUBLIC) {
            return true;
        }
        if (trip.getVisibility() == TripVisibility.FOLLOWERS_ONLY) {
            return viewer.map(v -> followRepository.existsByFollowerIdAndFolloweeId(v.getId(), trip.getUser().getId()))
                    .orElse(false);
        }
        return false; // PRIVATE
    }
}
