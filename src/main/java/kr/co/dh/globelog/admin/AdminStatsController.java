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

    public AdminStatsController(StatsService statsService) {
        this.statsService = statsService;
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
}
