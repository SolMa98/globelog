package kr.co.dh.globelog.security;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import kr.co.dh.globelog.security.totp.QrCodeGenerator;
import kr.co.dh.globelog.security.totp.TotpService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 로그인 사용자 본인의 2차 인증(TOTP/이메일 OTP) 설정 화면(/my/security).
 * TOTP는 6자리 코드로 확정하기 전까지 시크릿을 세션에만 잠깐 두고(PENDING_TOTP_SECRET),
 * 확정돼야 User.totpSecret/totpEnabled에 반영한다 — 등록 중단 시 잘못된 시크릿이 남지 않게.
 */
@Controller
public class SecuritySettingsController {

    private static final String PENDING_TOTP_SECRET_SESSION_KEY = "PENDING_TOTP_SECRET";
    private static final String ISSUER = "Globelog";

    private final UserRepository userRepository;
    private final CurrentUserResolver currentUserResolver;
    private final TotpService totpService;
    private final QrCodeGenerator qrCodeGenerator;

    public SecuritySettingsController(
            UserRepository userRepository,
            CurrentUserResolver currentUserResolver,
            TotpService totpService,
            QrCodeGenerator qrCodeGenerator) {
        this.userRepository = userRepository;
        this.currentUserResolver = currentUserResolver;
        this.totpService = totpService;
        this.qrCodeGenerator = qrCodeGenerator;
    }

    @GetMapping("/my/security")
    public String page(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("totpEnabled", user.isTotpEnabled());
        model.addAttribute("emailOtpEnabled", user.isEmailOtpEnabled());
        return "security-settings";
    }

    @PostMapping("/my/security/totp/setup")
    public String setupTotp(Authentication authentication, HttpServletRequest request, Model model) {
        User user = currentUser(authentication);
        String secret = totpService.generateSecret();
        request.getSession(true).setAttribute(PENDING_TOTP_SECRET_SESSION_KEY, secret);

        String otpAuthUri = totpService.buildOtpAuthUri(secret, user.getEmail(), ISSUER);
        model.addAttribute("totpEnabled", user.isTotpEnabled());
        model.addAttribute("emailOtpEnabled", user.isEmailOtpEnabled());
        model.addAttribute("qrDataUri", qrCodeGenerator.toPngDataUri(otpAuthUri));
        model.addAttribute("secret", secret);
        return "security-settings";
    }

    @PostMapping("/my/security/totp/confirm")
    public String confirmTotp(
            Authentication authentication, @RequestParam String code, HttpServletRequest request, Model model) {
        User user = currentUser(authentication);
        String pendingSecret = (String) request.getSession(true).getAttribute(PENDING_TOTP_SECRET_SESSION_KEY);

        if (pendingSecret == null || !totpService.verifyCode(pendingSecret, code)) {
            model.addAttribute("totpEnabled", user.isTotpEnabled());
            model.addAttribute("emailOtpEnabled", user.isEmailOtpEnabled());
            model.addAttribute("error", "인증 코드가 올바르지 않습니다. 다시 시도해주세요.");
            return "security-settings";
        }

        request.getSession(true).removeAttribute(PENDING_TOTP_SECRET_SESSION_KEY);
        user.setTotpSecret(pendingSecret);
        user.setTotpEnabled(true);
        userRepository.save(user);
        model.addAttribute("totpEnabled", true);
        model.addAttribute("emailOtpEnabled", user.isEmailOtpEnabled());
        model.addAttribute("message", "인증 앱(Google OTP)이 등록됐습니다.");
        return "security-settings";
    }

    @PostMapping("/my/security/totp/disable")
    public String disableTotp(Authentication authentication) {
        User user = currentUser(authentication);
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
        return "redirect:/my/security";
    }

    @PostMapping("/my/security/email-otp/toggle")
    public String toggleEmailOtp(Authentication authentication) {
        User user = currentUser(authentication);
        user.setEmailOtpEnabled(!user.isEmailOtpEnabled());
        userRepository.save(user);
        return "redirect:/my/security";
    }

    private User currentUser(Authentication authentication) {
        return currentUserResolver.resolve(authentication)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED));
    }
}
