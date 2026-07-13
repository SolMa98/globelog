package kr.co.dh.globelog.admin;

import kr.co.dh.globelog.domain.AdminAccount;
import kr.co.dh.globelog.domain.AdminAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 관리자 본인 비밀번호 변경 — mustChangePassword 플래그가 켜진 계정은
 * AdminMustChangePasswordInterceptor에 의해 이 화면으로만 접근이 강제된다.
 */
@Controller
public class AdminPasswordController {

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminPasswordController(AdminAccountRepository adminAccountRepository, PasswordEncoder passwordEncoder) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/admin/change-password")
    public String form(Authentication authentication, Model model) {
        AdminAccount account = currentAccount(authentication);
        model.addAttribute("forced", account.isMustChangePassword());
        return "admin/change-password";
    }

    @PostMapping("/admin/change-password")
    public String change(
            Authentication authentication,
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Model model) {
        AdminAccount account = currentAccount(authentication);
        model.addAttribute("forced", account.isMustChangePassword());

        if (!passwordEncoder.matches(currentPassword, account.getPassword())) {
            model.addAttribute("error", "현재 비밀번호가 일치하지 않습니다.");
            return "admin/change-password";
        }
        if (newPassword.length() < 8) {
            model.addAttribute("error", "새 비밀번호는 8자 이상이어야 합니다.");
            return "admin/change-password";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "새 비밀번호 확인이 일치하지 않습니다.");
            return "admin/change-password";
        }
        if (passwordEncoder.matches(newPassword, account.getPassword())) {
            model.addAttribute("error", "현재 비밀번호와 다른 비밀번호를 입력해주세요.");
            return "admin/change-password";
        }

        account.changePassword(passwordEncoder.encode(newPassword));
        adminAccountRepository.save(account);
        return "redirect:/admin/countries?passwordChanged";
    }

    private AdminAccount currentAccount(Authentication authentication) {
        return adminAccountRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("로그인한 관리자 계정을 찾을 수 없습니다."));
    }
}