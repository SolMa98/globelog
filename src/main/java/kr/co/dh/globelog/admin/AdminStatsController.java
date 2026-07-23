package kr.co.dh.globelog.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin/stats")
public class AdminStatsController {

    private final StatsService statsService;
    private final ActivityStatsService activityStatsService;
    private final FileStorageStatsService fileStorageStatsService;

    public AdminStatsController(StatsService statsService, ActivityStatsService activityStatsService,
            FileStorageStatsService fileStorageStatsService) {
        this.statsService = statsService;
        this.activityStatsService = activityStatsService;
        this.fileStorageStatsService = fileStorageStatsService;
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
}
