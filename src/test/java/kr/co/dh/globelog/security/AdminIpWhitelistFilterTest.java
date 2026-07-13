package kr.co.dh.globelog.security;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import kr.co.dh.globelog.domain.AdminIpWhitelistEntry;
import kr.co.dh.globelog.domain.AdminIpWhitelistEntryRepository;
import org.junit.jupiter.api.Test;

class AdminIpWhitelistFilterTest {

    private final AdminIpWhitelistEntryRepository repository = mock(AdminIpWhitelistEntryRepository.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @Test
    void 비활성화_상태면_화이트리스트와_무관하게_통과시킨다() throws Exception {
        AdminIpWhitelistFilter filter = new AdminIpWhitelistFilter(repository, false);
        HttpServletRequest request = requestFrom("203.0.113.99");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void loopback은_화이트리스트가_비어있어도_항상_통과한다() throws Exception {
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        AdminIpWhitelistFilter filter = new AdminIpWhitelistFilter(repository, true);
        HttpServletRequest request = requestFrom("127.0.0.1");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void 등록된_단일_IP는_통과한다() throws Exception {
        when(repository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(new AdminIpWhitelistEntry("203.0.113.5", "office")));
        AdminIpWhitelistFilter filter = new AdminIpWhitelistFilter(repository, true);
        HttpServletRequest request = requestFrom("203.0.113.5");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void 등록된_CIDR_대역은_통과한다() throws Exception {
        when(repository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(new AdminIpWhitelistEntry("192.168.1.0/24", "home")));
        AdminIpWhitelistFilter filter = new AdminIpWhitelistFilter(repository, true);
        HttpServletRequest request = requestFrom("192.168.1.42");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void 화이트리스트에_없는_IP는_403으로_차단된다() throws Exception {
        when(repository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(new AdminIpWhitelistEntry("203.0.113.5", "office")));
        AdminIpWhitelistFilter filter = new AdminIpWhitelistFilter(repository, true);
        HttpServletRequest request = requestFrom("198.51.100.9");

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response, times(1)).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }

    @Test
    void 형식이_잘못된_항목은_무시하고_나머지로_계속_비교한다() throws Exception {
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                new AdminIpWhitelistEntry("not-an-ip", "broken"),
                new AdminIpWhitelistEntry("203.0.113.5", "office")));
        AdminIpWhitelistFilter filter = new AdminIpWhitelistFilter(repository, true);
        HttpServletRequest request = requestFrom("203.0.113.5");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private HttpServletRequest requestFrom(String remoteAddr) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getRequestURI()).thenReturn("/admin/login");
        when(request.getDispatcherType()).thenReturn(jakarta.servlet.DispatcherType.REQUEST);
        return request;
    }
}
