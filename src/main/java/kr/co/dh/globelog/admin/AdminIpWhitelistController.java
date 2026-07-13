package kr.co.dh.globelog.admin;

import java.util.Map;
import kr.co.dh.globelog.domain.AdminIpWhitelistEntry;
import kr.co.dh.globelog.domain.AdminIpWhitelistEntryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin/ip-whitelist")
public class AdminIpWhitelistController {

    private final AdminIpWhitelistEntryRepository repository;

    public AdminIpWhitelistController(AdminIpWhitelistEntryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("entries", repository.findAllByOrderByCreatedAtDesc());
        model.addAttribute("activeMenu", "ipWhitelist");
        return "admin/ip-whitelist/list";
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam String cidr, @RequestParam(required = false) String description) {
        String trimmed = cidr.trim();
        try {
            // 등록 시점에 형식을 미리 검증 — 잘못된 값이 저장돼서 필터가 조용히 무시하는 상황 방지.
            new IpAddressMatcher(trimmed);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "IP/CIDR 형식이 올바르지 않습니다: " + trimmed));
        }
        if (repository.existsByCidr(trimmed)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "이미 등록된 항목입니다: " + trimmed));
        }

        repository.save(new AdminIpWhitelistEntry(trimmed, description));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
