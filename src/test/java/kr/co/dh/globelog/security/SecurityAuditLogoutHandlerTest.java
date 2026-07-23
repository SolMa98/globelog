package kr.co.dh.globelog.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.co.dh.globelog.domain.AdminAccount;
import kr.co.dh.globelog.domain.AdminAccountRepository;
import kr.co.dh.globelog.domain.AdminRole;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventType;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import kr.co.dh.globelog.security.audit.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * admin/user 두 인증 영역의 principal 클래스가 둘 다 스프링 기본 User라 instanceof로는
 * 구분이 안 되므로(SecurityAuditLogoutHandler 클래스 주석 참고), 권한(ROLE_*)으로
 * 관리자/일반 사용자를 정확히 갈라 각자 다른 리포지토리에서 조회하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class SecurityAuditLogoutHandlerTest {

    @Mock
    private AdminAccountRepository adminAccountRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SecurityAuditService securityAuditService;

    @Test
    void 관리자_권한이면_AdminAccount에서_조회해서_기록한다() {
        AdminAccount admin = new AdminAccount("admin", "encoded", AdminRole.SUPER_ADMIN);
        when(adminAccountRepository.findByUsername("admin")).thenReturn(java.util.Optional.of(admin));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "admin", "N/A", List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));

        SecurityAuditLogoutHandler handler =
                new SecurityAuditLogoutHandler(adminAccountRepository, userRepository, securityAuditService);
        handler.logout(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication);

        verify(securityAuditService).record(SecurityEventType.LOGOUT, SecurityActorType.ADMIN,
                admin.getId(), "admin", null, null, null);
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void 일반_사용자_권한이면_User에서_조회해서_기록한다() {
        User user = new User("a@b.com", "encoded", "tester");
        when(userRepository.findByEmail("a@b.com")).thenReturn(java.util.Optional.of(user));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "a@b.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        SecurityAuditLogoutHandler handler =
                new SecurityAuditLogoutHandler(adminAccountRepository, userRepository, securityAuditService);
        handler.logout(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication);

        verify(securityAuditService).record(SecurityEventType.LOGOUT, SecurityActorType.USER,
                user.getId(), "tester", null, null, null);
        verify(adminAccountRepository, never()).findByUsername(any());
    }
}
