package kr.co.dh.globelog.mytrip;

import kr.co.dh.globelog.admin.AdminStatsResponse;
import kr.co.dh.globelog.admin.StatsService;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.security.CurrentUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * 로그인한 사용자 본인의 여행 통계(/my/stats) — /admin/stats(전체 사용자 합산, 관리자
 * 전용)와 완전히 분리된 화면. 집계 로직은 StatsService.computeForUser()를 그대로 재사용.
 */
@Controller
@RequestMapping("/my/stats")
public class MyStatsController {

    private final StatsService statsService;
    private final CurrentUserResolver currentUserResolver;

    public MyStatsController(StatsService statsService, CurrentUserResolver currentUserResolver) {
        this.statsService = statsService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public String page(Authentication authentication) {
        requireLoggedIn(authentication);
        return "mytrip/stats";
    }

    @GetMapping("/data")
    @ResponseBody
    public AdminStatsResponse data(Authentication authentication) {
        User viewer = requireLoggedIn(authentication);
        return statsService.computeForUser(viewer.getId());
    }

    private User requireLoggedIn(Authentication authentication) {
        return currentUserResolver.resolve(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }
}
