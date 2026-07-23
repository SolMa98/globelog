package kr.co.dh.globelog.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.dh.globelog.domain.AdminAccount;
import kr.co.dh.globelog.domain.AdminAccountRepository;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventType;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import kr.co.dh.globelog.security.audit.SecurityAuditService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * admin/app 두 SecurityFilterChain(SecurityConfig 참고)이 공유하는 로그아웃 감사 로그 기록기.
 * LogoutHandler는 LogoutSuccessHandler와 달리 SecurityContext가 비워지기 전에 실행되므로
 * Authentication을 그대로 넘겨받을 수 있다 — 이게 여기서 로그를 남기는 이유.
 *
 * admin/user는 principal 클래스가 둘 다 org.springframework.security.core.userdetails.User라
 * instanceof로 구분이 안 된다(AdminUserDetailsService/AppUserDetailsService 참고). 대신
 * 권한(ROLE_SUPER_ADMIN/ROLE_MODERATOR vs ROLE_USER)으로 구분한다.
 */
@Component
public class SecurityAuditLogoutHandler implements LogoutHandler {

    private final AdminAccountRepository adminAccountRepository;
    private final UserRepository userRepository;
    private final SecurityAuditService securityAuditService;

    public SecurityAuditLogoutHandler(AdminAccountRepository adminAccountRepository, UserRepository userRepository,
            SecurityAuditService securityAuditService) {
        this.adminAccountRepository = adminAccountRepository;
        this.userRepository = userRepository;
        this.securityAuditService = securityAuditService;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return;
        }
        if (isAdmin(authentication)) {
            AdminAccount account = adminAccountRepository.findByUsername(authentication.getName()).orElse(null);
            if (account != null) {
                securityAuditService.record(SecurityEventType.LOGOUT, SecurityActorType.ADMIN,
                        account.getId(), account.getUsername(), null, null, null);
            }
        } else {
            User user = userRepository.findByEmail(authentication.getName()).orElse(null);
            if (user != null) {
                securityAuditService.record(SecurityEventType.LOGOUT, SecurityActorType.USER,
                        user.getId(), user.getNickname(), null, null, null);
            }
        }
    }

    private boolean isAdmin(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_SUPER_ADMIN".equals(role) || "ROLE_MODERATOR".equals(role)) {
                return true;
            }
        }
        return false;
    }
}
