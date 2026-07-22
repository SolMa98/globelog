package kr.co.dh.globelog.profile;

import java.util.List;
import java.util.Optional;
import kr.co.dh.globelog.domain.Follow;
import kr.co.dh.globelog.domain.FollowRepository;
import kr.co.dh.globelog.domain.TripRepository;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import kr.co.dh.globelog.security.CurrentUserResolver;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * 사용자 프로필 페이지(/u/{nickname})와 팔로우/언팔로우 API.
 * "게시글/작성자를 클릭하면 그 사용자의 지구본으로 진입"하는 피드 연동 흐름의
 * 진입점 역할도 겸함(project_multiuser_design_discussion 메모리 참고).
 */
@Controller
public class ProfileController {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final TripRepository tripRepository;
    private final CurrentUserResolver currentUserResolver;

    public ProfileController(UserRepository userRepository, FollowRepository followRepository,
            TripRepository tripRepository, CurrentUserResolver currentUserResolver) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.tripRepository = tripRepository;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/u/{nickname}")
    public String page(@PathVariable String nickname, Model model) {
        model.addAttribute("nickname", nickname);
        return "profile";
    }

    // 지구본은 항상 특정 사용자 소유 — URL 자체가 "누구의 지구본인지" 스코프를 정함.
    // globe.js가 window.location.pathname에서 nickname을 파싱해 API 호출에 실어보낸다.
    @GetMapping("/u/{nickname}/globe")
    public String globePage(@PathVariable String nickname) {
        return "forward:/globe.html";
    }

    @GetMapping("/api/users/{nickname}")
    @ResponseBody
    public UserProfileResponse profile(@PathVariable String nickname, Authentication authentication) {
        return buildResponse(findUserOrThrow(nickname), authentication);
    }

    // 채팅 "새 대화 시작" 검색용 — 닉네임 부분일치 검색. 이메일은 검색 대상에서 제외해
    // 이메일 주소로 계정을 알아내는 용도로 악용되지 않게 한다.
    @GetMapping("/api/users/search")
    @ResponseBody
    public List<UserSummaryResponse> search(@RequestParam String q, Authentication authentication) {
        User viewer = requireLoggedIn(authentication);
        String query = q.trim();
        if (query.isEmpty()) {
            return List.of();
        }
        return userRepository.findByNicknameContainingIgnoreCase(query, PageRequest.of(0, 20)).stream()
                .filter(u -> !u.getId().equals(viewer.getId()))
                .map(ProfileController::toSummary)
                .toList();
    }

    @GetMapping("/api/users/{nickname}/following")
    @ResponseBody
    public List<UserSummaryResponse> following(@PathVariable String nickname, Authentication authentication) {
        requireLoggedIn(authentication);
        User target = findUserOrThrow(nickname);
        return followRepository.findByFollowerId(target.getId()).stream()
                .map(Follow::getFollowee)
                .map(ProfileController::toSummary)
                .toList();
    }

    @PostMapping("/api/users/{nickname}/follow")
    @ResponseBody
    public UserProfileResponse follow(@PathVariable String nickname, Authentication authentication) {
        User viewer = requireLoggedIn(authentication);
        User target = findUserOrThrow(nickname);
        if (viewer.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 팔로우할 수 없습니다.");
        }
        if (!followRepository.existsByFollowerIdAndFolloweeId(viewer.getId(), target.getId())) {
            followRepository.save(new Follow(viewer, target));
        }
        return buildResponse(target, authentication);
    }

    @DeleteMapping("/api/users/{nickname}/follow")
    @ResponseBody
    @Transactional
    public UserProfileResponse unfollow(@PathVariable String nickname, Authentication authentication) {
        User viewer = requireLoggedIn(authentication);
        User target = findUserOrThrow(nickname);
        followRepository.deleteByFollowerIdAndFolloweeId(viewer.getId(), target.getId());
        return buildResponse(target, authentication);
    }

    private User findUserOrThrow(String nickname) {
        return userRepository.findByNickname(nickname)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다: " + nickname));
    }

    private User requireLoggedIn(Authentication authentication) {
        return currentUserResolver.resolve(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }

    private UserProfileResponse buildResponse(User target, Authentication authentication) {
        long followerCount = followRepository.countByFolloweeId(target.getId());
        long followingCount = followRepository.countByFollowerId(target.getId());
        long tripCount = tripRepository.countByUserId(target.getId());
        long visitedCountryCount = tripRepository.countDistinctCountryByUserId(target.getId());
        Optional<User> viewer = currentUserResolver.resolve(authentication);
        boolean isSelf = viewer.map(v -> v.getId().equals(target.getId())).orElse(false);
        boolean isFollowing = viewer.filter(v -> !isSelf)
                .map(v -> followRepository.existsByFollowerIdAndFolloweeId(v.getId(), target.getId()))
                .orElse(false);
        return new UserProfileResponse(target.getId(), target.getNickname(), target.getProfileImageUrl(),
                target.getBio(), followerCount, followingCount, tripCount, visitedCountryCount,
                target.getCreatedAt().getYear(), isFollowing, isSelf);
    }

    private static UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }
}
