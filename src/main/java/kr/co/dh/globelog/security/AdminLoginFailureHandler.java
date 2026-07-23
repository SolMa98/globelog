package kr.co.dh.globelog.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventType;
import kr.co.dh.globelog.security.audit.SecurityAuditService;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * 관리자 로그인 실패 보안 로그. 사용자가 입력한 아이디만 기록하고 비밀번호 파라미터는
 * 절대 읽지 않는다(LoginFailureHandler와 동일한 이유).
 */
@Component
public class AdminLoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final SecurityAuditService securityAuditService;

    public AdminLoginFailureHandler(SecurityAuditService securityAuditService) {
        super("/admin/login?error");
        this.securityAuditService = securityAuditService;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        securityAuditService.record(SecurityEventType.LOGIN_FAILURE, SecurityActorType.ADMIN,
                null, request.getParameter("username"), null, null, exception.getMessage());
        super.onAuthenticationFailure(request, response, exception);
    }
}
