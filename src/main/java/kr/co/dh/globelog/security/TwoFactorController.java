package kr.co.dh.globelog.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import kr.co.dh.globelog.mail.MailService;
import kr.co.dh.globelog.security.totp.TotpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 로그인 1단계(비밀번호/소셜) 통과 후, 계정에 2차 인증이 켜져 있으면 이어지는 코드 입력 게이트.
 * TwoFactorAndOnboardingSuccessHandler가 완성된 Authentication을 세션에 보관해두고 여기로 보낸다.
 *
 * 검증 성공 시 원래 목적지(딥링크)로 되돌리는 대신 항상 "/"로 보낸다 — 2FA 단계를 거치는
 * 로그인은 흔치 않은 경로라, SavedRequest를 굳이 여기까지 이어받는 복잡도를 들이지 않았다.
 */
@Controller
public class TwoFactorController {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorController.class);
    private static final String EMAIL_OTP_CODE_SESSION_KEY = "EMAIL_OTP_CODE";
    private static final String EMAIL_OTP_EXPIRES_AT_SESSION_KEY = "EMAIL_OTP_EXPIRES_AT";
    private static final int EMAIL_OTP_VALID_MINUTES = 5;

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final MailService mailService;
    private final SecurityContextRepository securityContextRepository;

    public TwoFactorController(
            UserRepository userRepository,
            TotpService totpService,
            MailService mailService,
            SecurityContextRepository securityContextRepository) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.mailService = mailService;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/login/2fa")
    public String page(HttpServletRequest request, Model model) {
        User user = pendingUser(request);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("totpEnabled", user.isTotpEnabled());
        model.addAttribute("emailOtpEnabled", user.isEmailOtpEnabled());
        return "login-2fa";
    }

    @PostMapping("/login/2fa/verify-totp")
    public String verifyTotp(
            @RequestParam String code, HttpServletRequest request, HttpServletResponse response, Model model) {
        User user = pendingUser(request);
        if (user == null) {
            return "redirect:/login";
        }
        if (!user.isTotpEnabled() || !totpService.verifyCode(user.getTotpSecret(), code)) {
            model.addAttribute("totpEnabled", user.isTotpEnabled());
            model.addAttribute("emailOtpEnabled", user.isEmailOtpEnabled());
            model.addAttribute("error", "인증 코드가 올바르지 않습니다.");
            return "login-2fa";
        }
        return finish(request, response);
    }

    @PostMapping("/login/2fa/email-code/send")
    @ResponseBody
    public void sendEmailCode(HttpServletRequest request) {
        User user = pendingUser(request);
        if (user == null || !user.isEmailOtpEnabled()) {
            return;
        }
        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        HttpSession session = request.getSession(true);
        session.setAttribute(EMAIL_OTP_CODE_SESSION_KEY, code);
        session.setAttribute(EMAIL_OTP_EXPIRES_AT_SESSION_KEY, LocalDateTime.now().plusMinutes(EMAIL_OTP_VALID_MINUTES));
        try {
            mailService.sendOtpEmail(user.getEmail(), code);
        } catch (MailException e) {
            log.error("이메일 OTP 발송 실패: {}", user.getEmail(), e);
        }
    }

    @PostMapping("/login/2fa/verify-email-code")
    public String verifyEmailCode(
            @RequestParam String code, HttpServletRequest request, HttpServletResponse response, Model model) {
        User user = pendingUser(request);
        if (user == null) {
            return "redirect:/login";
        }

        HttpSession session = request.getSession(true);
        String expectedCode = (String) session.getAttribute(EMAIL_OTP_CODE_SESSION_KEY);
        LocalDateTime expiresAt = (LocalDateTime) session.getAttribute(EMAIL_OTP_EXPIRES_AT_SESSION_KEY);
        boolean valid = expectedCode != null && expiresAt != null && expiresAt.isAfter(LocalDateTime.now())
                && expectedCode.equals(code);

        if (!valid) {
            model.addAttribute("totpEnabled", user.isTotpEnabled());
            model.addAttribute("emailOtpEnabled", user.isEmailOtpEnabled());
            model.addAttribute("error", "인증 코드가 올바르지 않거나 만료됐습니다.");
            return "login-2fa";
        }
        session.removeAttribute(EMAIL_OTP_CODE_SESSION_KEY);
        session.removeAttribute(EMAIL_OTP_EXPIRES_AT_SESSION_KEY);
        return finish(request, response);
    }

    private String finish(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(true);
        Authentication pending = (Authentication)
                session.getAttribute(TwoFactorAndOnboardingSuccessHandler.PRE_2FA_AUTHENTICATION_SESSION_KEY);
        session.removeAttribute(TwoFactorAndOnboardingSuccessHandler.PRE_2FA_AUTHENTICATION_SESSION_KEY);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(pending);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return "redirect:/";
    }

    private User pendingUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Authentication pending = (Authentication)
                session.getAttribute(TwoFactorAndOnboardingSuccessHandler.PRE_2FA_AUTHENTICATION_SESSION_KEY);
        if (pending == null) {
            return null;
        }
        return userRepository.findByEmail(pending.getName()).orElse(null);
    }
}
