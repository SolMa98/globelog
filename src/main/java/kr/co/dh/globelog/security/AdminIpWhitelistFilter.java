package kr.co.dh.globelog.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.co.dh.globelog.domain.AdminIpWhitelistEntry;
import kr.co.dh.globelog.domain.AdminIpWhitelistEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * app.admin.ip-whitelist.enabled가 true일 때만 동작 — /admin/** 전체(로그인 화면 포함)를
 * DB에 등록된 IP/CIDR 목록으로만 허용한다. Spring Security 필터 체인 안에서
 * UsernamePasswordAuthenticationFilter보다 앞에 꽂아서, 화이트리스트 밖 IP는 로그인
 * 시도 자체가 처리되기 전에 차단되게 한다(SecurityConfig 참고).
 *
 * loopback(127.0.0.1/::1)은 화이트리스트가 비어 있거나 잘못 설정돼도 서버에 직접
 * 접속(SSH 등)해서 복구할 수 있도록 항상 허용한다 — 그래야 화이트리스트 실수로
 * 관리자 전원이 잠기는 상황을 피할 수 있다.
 */
public class AdminIpWhitelistFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminIpWhitelistFilter.class);

    private final AdminIpWhitelistEntryRepository repository;
    private final boolean enabled;

    public AdminIpWhitelistFilter(AdminIpWhitelistEntryRepository repository, boolean enabled) {
        this.repository = repository;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String remoteAddr = request.getRemoteAddr();

        if (!enabled || isLoopback(remoteAddr) || isWhitelisted(remoteAddr)) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("화이트리스트에 없는 IP의 관리자 접근을 차단했습니다: {} {}", remoteAddr, request.getRequestURI());
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "허용되지 않은 IP입니다.");
    }

    private boolean isLoopback(String remoteAddr) {
        return "127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr);
    }

    private boolean isWhitelisted(String remoteAddr) {
        for (AdminIpWhitelistEntry entry : repository.findAllByOrderByCreatedAtDesc()) {
            if (matches(entry.getCidr(), remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String cidr, String remoteAddr) {
        try {
            return new IpAddressMatcher(cidr).matches(remoteAddr);
        } catch (IllegalArgumentException e) {
            log.warn("화이트리스트 항목 형식이 잘못돼 무시합니다: {}", cidr);
            return false;
        }
    }
}
