package kr.co.dh.globelog.mytrip;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import kr.co.dh.globelog.admin.ActivityStatsResponse;
import kr.co.dh.globelog.admin.ActivityStatsService;
import kr.co.dh.globelog.admin.AdminStatsResponse;
import kr.co.dh.globelog.admin.StatsExcelWriter;
import kr.co.dh.globelog.admin.StatsService;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventType;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.security.CurrentUserResolver;
import kr.co.dh.globelog.security.audit.SecurityAuditService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    private static final DateTimeFormatter EXPORT_FILENAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final StatsService statsService;
    private final ActivityStatsService activityStatsService;
    private final CurrentUserResolver currentUserResolver;
    private final StatsExcelWriter statsExcelWriter;
    private final SecurityAuditService securityAuditService;

    public MyStatsController(StatsService statsService, ActivityStatsService activityStatsService,
            CurrentUserResolver currentUserResolver, StatsExcelWriter statsExcelWriter,
            SecurityAuditService securityAuditService) {
        this.statsService = statsService;
        this.activityStatsService = activityStatsService;
        this.currentUserResolver = currentUserResolver;
        this.statsExcelWriter = statsExcelWriter;
        this.securityAuditService = securityAuditService;
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

    @GetMapping("/activity")
    @ResponseBody
    public ActivityStatsResponse activity(Authentication authentication) {
        User viewer = requireLoggedIn(authentication);
        return activityStatsService.computeForUser(viewer.getId());
    }

    // 개인 통계는 파일 저장 용량(관리자 전용) 없이 여행 커버리지 + 활동 통계만 담는다.
    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<byte[]> export(Authentication authentication) {
        User viewer = requireLoggedIn(authentication);
        byte[] excelBytes = statsExcelWriter.write(
                statsService.computeForUser(viewer.getId()), activityStatsService.computeForUser(viewer.getId()),
                null);
        String filename = "my-stats-" + LocalDateTime.now().format(EXPORT_FILENAME_FORMAT) + ".xlsx";

        securityAuditService.record(SecurityEventType.EXCEL_EXPORT, SecurityActorType.USER,
                viewer.getId(), viewer.getNickname(), "MY_STATS", null, "내 통계 다운로드");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(excelBytes);
    }

    private User requireLoggedIn(Authentication authentication) {
        return currentUserResolver.resolve(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }
}
