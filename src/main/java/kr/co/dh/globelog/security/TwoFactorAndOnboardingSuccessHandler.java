package kr.co.dh.globelog.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import kr.co.dh.globelog.security.oauth.CustomOAuth2UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * formLogin(이메일/비밀번호)과 oauth2Login(Google/Naver/Kakao) 양쪽이 공유하는 로그인 성공 핸들러.
 * 두 경우 모두 Authentication.getName()이 도메인 User의 email과 같도록 맞춰져 있어
 * (CustomOAuth2UserService, AppUserDetailsService 참고) 여기서는 로그인 수단을 구분할 필요가 없다.
 *
 * 처리 순서:
 * 1) 소셜 최초 로그인(닉네임 미정, ROLE_PRE_SIGNUP)이면 온보딩 화면으로
 * 2) 계정에 2차 인증이 켜져 있으면 완성된 Authentication을 세션에 잠시 보관하고 인증 상태를
 *    비운 뒤 코드 입력 화면으로(TwoFactorController가 이어받음)
 * 3) 둘 다 아니면 기존과 동일하게 원래 목적지(or "/")로 이동
 */
@Component
public class TwoFactorAndOnboardingSuccessHandler implements AuthenticationSuccessHandler {

    public static final String PRE_2FA_AUTHENTICATION_SESSION_KEY = "PRE_2FA_AUTHENTICATION";

    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository;
    private final AuthenticationSuccessHandler delegate;

    public TwoFactorAndOnboardingSuccessHandler(
            UserRepository userRepository, SecurityContextRepository securityContextRepository) {
        this.userRepository = userRepository;
        this.securityContextRepository = securityContextRepository;
        SavedRequestAwareAuthenticationSuccessHandler savedRequestHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        savedRequestHandler.setDefaultTargetUrl("/");
        savedRequestHandler.setAlwaysUseDefaultTargetUrl(false);
        this.delegate = savedRequestHandler;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        boolean isPreSignup = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(CustomOAuth2UserService.PRE_SIGNUP_AUTHORITY));
        if (isPreSignup) {
            response.sendRedirect(request.getContextPath() + "/signup/social/complete");
            return;
        }

        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user != null && user.isTwoFactorEnabled()) {
            request.getSession(true).setAttribute(PRE_2FA_AUTHENTICATION_SESSION_KEY, authentication);
            // 세션에 이미 저장된 완전 인증 상태를 지우지 않으면, 2차 인증 없이도 다음 요청부터
            // SecurityContextHolderFilter가 세션에서 인증 완료 상태를 그대로 복원해버린다.
            SecurityContextHolder.clearContext();
            securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
            response.sendRedirect(request.getContextPath() + "/login/2fa");
            return;
        }

        delegate.onAuthenticationSuccess(request, response, authentication);
    }
}
