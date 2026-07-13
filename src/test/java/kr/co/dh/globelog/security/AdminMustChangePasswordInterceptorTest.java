package kr.co.dh.globelog.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.dh.globelog.domain.AdminAccount;
import kr.co.dh.globelog.domain.AdminAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminMustChangePasswordInterceptorTest {

    private AdminAccountRepository adminAccountRepository;
    private AdminMustChangePasswordInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        adminAccountRepository = mock(AdminAccountRepository.class);
        interceptor = new AdminMustChangePasswordInterceptor(adminAccountRepository);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(request.getContextPath()).thenReturn("");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedRequestPassesThrough() throws Exception {
        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(response, never()).sendRedirect(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void redirectsAndBlocksWhenPasswordChangeRequired() throws Exception {
        authenticateAs("admin");
        AdminAccount account = new AdminAccount("admin", "encoded");
        when(adminAccountRepository.findByUsername("admin")).thenReturn(Optional.of(account));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        verify(response).sendRedirect("/admin/change-password");
    }

    @Test
    void passesThroughWhenPasswordAlreadyChanged() throws Exception {
        authenticateAs("admin");
        AdminAccount account = new AdminAccount("admin", "encoded");
        account.changePassword("new-encoded");
        when(adminAccountRepository.findByUsername("admin")).thenReturn(Optional.of(account));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(response, never()).sendRedirect(org.mockito.ArgumentMatchers.anyString());
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "N/A", java.util.List.of()));
    }
}