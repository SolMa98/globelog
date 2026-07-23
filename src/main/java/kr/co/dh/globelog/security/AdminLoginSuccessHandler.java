package kr.co.dh.globelog.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.co.dh.globelog.domain.AdminAccount;
import kr.co.dh.globelog.domain.AdminAccountRepository;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventType;
import kr.co.dh.globelog.security.audit.SecurityAuditService;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 관리자 로그인 성공 시 보안 로그를 남긴 뒤, 기존과 동일하게 항상 /admin/countries로 보낸다
 * (예전엔 formLogin(...).defaultSuccessUrl("/admin/countries", true)로만 처리했음).
 */
@Component
public class AdminLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AdminAccountRepository adminAccountRepository;
    private final SecurityAuditService securityAuditService;

    public AdminLoginSuccessHandler(
            AdminAccountRepository adminAccountRepository, SecurityAuditService securityAuditService) {
        this.adminAccountRepository = adminAccountRepository;
        this.securityAuditService = securityAuditService;
        setDefaultTargetUrl("/admin/countries");
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        AdminAccount account = adminAccountRepository.findByUsername(authentication.getName()).orElse(null);
        if (account != null) {
            securityAuditService.record(SecurityEventType.LOGIN_SUCCESS, SecurityActorType.ADMIN,
                    account.getId(), account.getUsername(), null, null, null);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
