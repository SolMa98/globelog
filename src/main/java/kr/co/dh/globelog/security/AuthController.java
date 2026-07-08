package kr.co.dh.globelog.security;

import java.time.LocalDateTime;
import java.util.UUID;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import kr.co.dh.globelog.identity.DuplicateIdentityException;
import kr.co.dh.globelog.identity.IdentityVerificationFailedException;
import kr.co.dh.globelog.identity.IdentityVerificationResult;
import kr.co.dh.globelog.identity.IdentityVerificationService;
import kr.co.dh.globelog.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final int VERIFICATION_TOKEN_VALID_HOURS = 24;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final IdentityVerificationService identityVerificationService;
    private final String portoneStoreId;
    private final String portoneChannelKey;

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            MailService mailService,
            IdentityVerificationService identityVerificationService,
            @Value("${portone.store-id}") String portoneStoreId,
            @Value("${portone.channel-key}") String portoneChannelKey) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.identityVerificationService = identityVerificationService;
        this.portoneStoreId = portoneStoreId;
        this.portoneChannelKey = portoneChannelKey;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/signup")
    public String signupPage(Model model) {
        model.addAttribute("portoneStoreId", portoneStoreId);
        model.addAttribute("portoneChannelKey", portoneChannelKey);
        return "signup";
    }

    // 지구본은 이제 항상 특정 사용자 소유(/u/{nickname}/globe)라 대상 없는 /globe는
    // 폐지 — 옛 북마크/링크는 피드로 보낸다.
    @GetMapping("/globe")
    public String globePage() {
        return "redirect:/";
    }

    @PostMapping("/signup")
    public String signup(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String nickname,
            @RequestParam(required = false) String identityVerificationId,
            Model model) {
        model.addAttribute("portoneStoreId", portoneStoreId);
        model.addAttribute("portoneChannelKey", portoneChannelKey);

        String trimmedEmail = email == null ? "" : email.trim();
        String trimmedNickname = nickname == null ? "" : nickname.trim();

        if (trimmedEmail.isBlank() || password == null || password.length() < 8 || trimmedNickname.isBlank()) {
            model.addAttribute("error", "이메일, 8자 이상 비밀번호, 닉네임을 모두 입력해주세요.");
            return "signup";
        }
        if (userRepository.existsByEmail(trimmedEmail)) {
            model.addAttribute("error", "이미 가입된 이메일입니다.");
            return "signup";
        }
        if (userRepository.existsByNickname(trimmedNickname)) {
            model.addAttribute("error", "이미 사용 중인 닉네임입니다.");
            return "signup";
        }

        IdentityVerificationResult identityResult;
        try {
            identityResult = identityVerificationService.verify(identityVerificationId);
        } catch (DuplicateIdentityException | IdentityVerificationFailedException e) {
            model.addAttribute("error", e.getMessage());
            return "signup";
        }

        User user = new User(trimmedEmail, passwordEncoder.encode(password), trimmedNickname);
        user.setIdentityVerified(true);
        user.setIdentityVerifiedAt(LocalDateTime.now());
        user.setIdentityDiHash(identityResult.diHash());
        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(VERIFICATION_TOKEN_VALID_HOURS));
        userRepository.save(user);

        // SMTP 미설정/일시 장애로 발송이 실패해도 가입 자체는 유지한다(계정은 이미 저장됨) —
        // 지금은 재발송 기능이 없어 이 경우 사용자가 직접 재가입/문의해야 하는 한계가 있음.
        try {
            mailService.sendVerificationEmail(trimmedEmail, token);
        } catch (MailException e) {
            log.error("인증 메일 발송 실패: {}", trimmedEmail, e);
        }
        return "redirect:/login?signup";
    }

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token) {
        return userRepository.findByVerificationToken(token)
                .filter(user -> user.getVerificationTokenExpiresAt() != null
                        && user.getVerificationTokenExpiresAt().isAfter(LocalDateTime.now()))
                .map(user -> {
                    user.setEmailVerified(true);
                    user.setVerificationToken(null);
                    user.setVerificationTokenExpiresAt(null);
                    userRepository.save(user);
                    return "redirect:/login?verified";
                })
                .orElseGet(() -> "redirect:/login?verifyFailed");
    }
}
