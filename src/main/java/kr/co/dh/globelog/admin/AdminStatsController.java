package kr.co.dh.globelog.admin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventType;
import kr.co.dh.globelog.security.audit.SecurityAuditService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin/stats")
public class AdminStatsController {

    private static final DateTimeFormatter EXPORT_FILENAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final StatsService statsService;
    private final ActivityStatsService activityStatsService;
    private final FileStorageStatsService fileStorageStatsService;
    private final StatsExcelWriter statsExcelWriter;
    private final SecurityAuditService securityAuditService;

    public AdminStatsController(StatsService statsService, ActivityStatsService activityStatsService,
            FileStorageStatsService fileStorageStatsService, StatsExcelWriter statsExcelWriter,
            SecurityAuditService securityAuditService) {
        this.statsService = statsService;
        this.activityStatsService = activityStatsService;
        this.fileStorageStatsService = fileStorageStatsService;
        this.statsExcelWriter = statsExcelWriter;
        this.securityAuditService = securityAuditService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("activeMenu", "stats");
        return "admin/stats/list";
    }

    @GetMapping("/data")
    @ResponseBody
    public AdminStatsResponse data() {
        return statsService.compute();
    }

    @GetMapping("/activity")
    @ResponseBody
    public ActivityStatsResponse activity() {
        return activityStatsService.compute();
    }

    // 파일 저장 용량은 관리자만 볼 수 있다 — /admin/stats/**가 이미 SecurityConfig에서
    // SUPER_ADMIN 전용으로 막혀 있어 이 엔드포인트도 자동으로 같은 제약을 받는다.
    @GetMapping("/storage")
    @ResponseBody
    public FileStorageStatsResponse storage() {
        return fileStorageStatsService.compute();
    }

    // 통계는 개인을 특정하지 않는 집계치라 보안 로그 다운로드처럼 사유/비밀번호까지
    // 요구하진 않지만, 다운로드 행위 자체는 동일하게 감사 로그에 남긴다.
    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<byte[]> export(Authentication authentication) {
        byte[] excelBytes = statsExcelWriter.write(
                statsService.compute(), activityStatsService.compute(), fileStorageStatsService.compute());
        String filename = "admin-stats-" + LocalDateTime.now().format(EXPORT_FILENAME_FORMAT) + ".xlsx";

        securityAuditService.record(SecurityEventType.EXCEL_EXPORT, SecurityActorType.ADMIN,
                null, authentication.getName(), "ADMIN_STATS", null, "관리자 통계 다운로드");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(excelBytes);
    }
}
