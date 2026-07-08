package kr.co.dh.globelog.admin;

import java.util.Map;
import kr.co.dh.globelog.domain.AdminAccount;
import kr.co.dh.globelog.domain.AdminAccountRepository;
import kr.co.dh.globelog.domain.AdminRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin/accounts")
public class AdminAccountController {

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountController(AdminAccountRepository adminAccountRepository, PasswordEncoder passwordEncoder) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 30, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {
        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        Page<AdminAccount> accounts = trimmedKeyword.isEmpty()
                ? adminAccountRepository.findAll(pageable)
                : adminAccountRepository.findByUsernameContainingIgnoreCase(trimmedKeyword, pageable);
        model.addAttribute("accounts", accounts);
        model.addAttribute("keyword", trimmedKeyword);
        model.addAttribute("activeMenu", "accounts");
        return "admin/accounts/list";
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(defaultValue = "SUPER_ADMIN") AdminRole role) {
        if (adminAccountRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "이미 존재하는 계정입니다: " + username));
        }

        adminAccountRepository.save(new AdminAccount(username, passwordEncoder.encode(password), role));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        adminAccountRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}