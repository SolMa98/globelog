package kr.co.dh.globelog.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.dh.globelog.domain.AdminAccount;
import kr.co.dh.globelog.domain.AdminAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * mustChangePassword가 true인 관리자 계정이 비밀번호를 바꾸기 전까지는 /admin/change-password
 * 외의 다른 어드민 화면에 접근하지 못하게 막는다(부트스트랩 기본 비밀번호 방치 방지).
 */
@Component
public class AdminMustChangePasswordInterceptor implements HandlerInterceptor {

    private final AdminAccountRepository adminAccountRepository;

    public AdminMustChangePasswordInterceptor(AdminAccountRepository adminAccountRepository) {
        this.adminAccountRepository = adminAccountRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return true;
        }

        AdminAccount account = adminAccountRepository.findByUsername(authentication.getName()).orElse(null);
        if (account != null && account.isMustChangePassword()) {
            response.sendRedirect(request.getContextPath() + "/admin/change-password");
            return false;
        }
        return true;
    }
}