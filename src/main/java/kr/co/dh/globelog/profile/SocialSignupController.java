package kr.co.dh.globelog.profile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import kr.co.dh.globelog.domain.SocialAccount;
import kr.co.dh.globelog.domain.SocialAccountRepository;
import kr.co.dh.globelog.domain.SocialProvider;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import kr.co.dh.globelog.identity.DuplicateIdentityException;
import kr.co.dh.globelog.identity.IdentityVerificationFailedException;
import kr.co.dh.globelog.identity.IdentityVerificationResult;
import kr.co.dh.globelog.identity.IdentityVerificationService;
import kr.co.dh.globelog.security.oauth.CustomOAuth2UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 소셜 로그인 최초 가입자의 닉네임 온보딩. CustomOAuth2UserService가 신규 계정에는
 * ROLE_PRE_SIGNUP 권한만 가진 임시 principal을 발급해두고(DB에는 아직 아무것도 안 씀),
 * 여기서 닉네임을 받아 실제 User+SocialAccount를 만든 뒤 정식 로그인 상태로 전환한다.
 */
@Controller
public class SocialSignupController {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final SecurityContextRepository securityContextRepository;
    private final IdentityVerificationService identityVerificationService;
    private final String portoneStoreId;
    private final String portoneChannelKey;

    public SocialSignupController(
            UserRepository userRepository,
            SocialAccountRepository socialAccountRepository,
            PasswordEncoder passwordEncoder,
            CustomOAuth2UserService customOAuth2UserService,
            SecurityContextRepository securityContextRepository,
            IdentityVerificationService identityVerificationService,
            @Value("${portone.store-id}") String portoneStoreId,
            @Value("${portone.channel-key}") String portoneChannelKey) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.customOAuth2UserService = customOAuth2UserService;
        this.securityContextRepository = securityContextRepository;
        this.identityVerificationService = identityVerificationService;
        this.portoneStoreId = portoneStoreId;
        this.portoneChannelKey = portoneChannelKey;
    }

    @GetMapping("/signup/social/complete")
    public String page(Authentication authentication, Model model) {
        if (!isPreSignup(authentication)) {
            return "redirect:/login";
        }
        model.addAttribute("email", authentication.getName());
        model.addAttribute("portoneStoreId", portoneStoreId);
        model.addAttribute("portoneChannelKey", portoneChannelKey);
        return "signup-social-complete";
    }

    @PostMapping("/signup/social/complete")
    public String complete(
            Authentication authentication,
            @RequestParam String nickname,
            @RequestParam(required = false) String identityVerificationId,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!isPreSignup(authentication)) {
            return "redirect:/login";
        }

        String trimmedNickname = nickname == null ? "" : nickname.trim();
        Map<String, Object> attributes = ((OAuth2User) authentication.getPrincipal()).getAttributes();
        String email = (String) attributes.get("email");
        String providerName = (String) attributes.get("provider");
        String providerId = (String) attributes.get("providerId");

        model.addAttribute("email", email);
        model.addAttribute("portoneStoreId", portoneStoreId);
        model.addAttribute("portoneChannelKey", portoneChannelKey);

        if (trimmedNickname.isBlank()) {
            model.addAttribute("error", "닉네임을 입력해주세요.");
            return "signup-social-complete";
        }
        if (userRepository.existsByNickname(trimmedNickname)) {
            model.addAttribute("error", "이미 사용 중인 닉네임입니다.");
            return "signup-social-complete";
        }

        IdentityVerificationResult identityResult;
        try {
            identityResult = identityVerificationService.verify(identityVerificationId);
        } catch (DuplicateIdentityException | IdentityVerificationFailedException e) {
            model.addAttribute("error", e.getMessage());
            return "signup-social-complete";
        }

        // 소셜 전용 가입자는 비밀번호 로그인을 쓰지 않으므로, 무작위 값으로 채워 비활성 상태로 둔다.
        User user = new User(email, passwordEncoder.encode(UUID.randomUUID().toString()), trimmedNickname);
        user.setEmailVerified(true);
        user.setIdentityVerified(true);
        user.setIdentityVerifiedAt(LocalDateTime.now());
        user.setIdentityDiHash(identityResult.diHash());
        userRepository.save(user);
        socialAccountRepository.save(new SocialAccount(user, SocialProvider.valueOf(providerName), providerId));

        completeLogin(user, providerName, request, response);
        return "redirect:/";
    }

    private void completeLogin(
            User user, String providerName, HttpServletRequest request, HttpServletResponse response) {
        OAuth2User principal = customOAuth2UserService.userPrincipal(user);
        Authentication newAuthentication =
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), providerName.toLowerCase());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(newAuthentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private boolean isPreSignup(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals(CustomOAuth2UserService.PRE_SIGNUP_AUTHORITY));
    }
}
