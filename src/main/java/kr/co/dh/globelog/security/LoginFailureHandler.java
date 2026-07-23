package kr.co.dh.globelog.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventType;
import kr.co.dh.globelog.security.audit.SecurityAuditService;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * 로그인 실패 사유별로 login.html에 다른 안내 문구를 보여주기 위해 리다이렉트 쿼리를 분기한다.
 */
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final SecurityAuditService securityAuditService;

    public LoginFailureHandler(SecurityAuditService securityAuditService) {
        super("/login?error");
        this.securityAuditService = securityAuditService;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        boolean isSocial = exception instanceof OAuth2AuthenticationException;
        if (exception instanceof EmailNotVerifiedException) {
            setDefaultFailureUrl("/login?error=unverified");
        } else if (isSocial) {
            setDefaultFailureUrl("/login?error=social");
        } else {
            setDefaultFailureUrl("/login?error");
        }
        // 폼 로그인은 사용자가 입력한 아이디(이메일)만 기록한다 — 비밀번호 파라미터는
        // 절대 읽지 않는다(평문 비밀번호가 감사 로그에 영구히 남는 사고를 막기 위함).
        String actorLabel = isSocial ? "소셜 로그인 시도" : request.getParameter("username");
        securityAuditService.record(SecurityEventType.LOGIN_FAILURE, SecurityActorType.USER,
                null, actorLabel, null, null, exception.getMessage());
        super.onAuthenticationFailure(request, response, exception);
    }
}
