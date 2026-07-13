package kr.co.dh.globelog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.co.dh.globelog.domain.AdminAccount;
import kr.co.dh.globelog.domain.AdminAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.Model;
import org.springframework.ui.ExtendedModelMap;

class AdminPasswordControllerTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AdminAccountRepository adminAccountRepository;
    private Authentication authentication;
    private AdminAccount account;
    private AdminPasswordController controller;
    private Model model;

    @BeforeEach
    void setUp() {
        adminAccountRepository = mock(AdminAccountRepository.class);
        authentication = mock(Authentication.class);
        model = new ExtendedModelMap();
        controller = new AdminPasswordController(adminAccountRepository, passwordEncoder);

        account = new AdminAccount("admin", passwordEncoder.encode("changeme123"));
        when(authentication.getName()).thenReturn("admin");
        when(adminAccountRepository.findByUsername("admin")).thenReturn(Optional.of(account));
    }

    @Test
    void wrongCurrentPasswordDoesNotChangePassword() {
        String view = controller.change(authentication, "wrong-password", "newpass123", "newpass123", model);

        assertThat(view).isEqualTo("admin/change-password");
        assertThat(model.getAttribute("error")).isNotNull();
        assertThat(account.isMustChangePassword()).isTrue();
        verify(adminAccountRepository, never()).save(account);
    }

    @Test
    void mismatchedConfirmationDoesNotChangePassword() {
        String view = controller.change(authentication, "changeme123", "newpass123", "different123", model);

        assertThat(view).isEqualTo("admin/change-password");
        assertThat(account.isMustChangePassword()).isTrue();
    }

    @Test
    void sameAsCurrentPasswordIsRejected() {
        String view = controller.change(authentication, "changeme123", "changeme123", "changeme123", model);

        assertThat(view).isEqualTo("admin/change-password");
        assertThat(account.isMustChangePassword()).isTrue();
    }

    @Test
    void successfulChangeClearsFlagAndSaves() {
        String view = controller.change(authentication, "changeme123", "newpass123", "newpass123", model);

        assertThat(view).isEqualTo("redirect:/admin/countries?passwordChanged");
        assertThat(account.isMustChangePassword()).isFalse();
        assertThat(passwordEncoder.matches("newpass123", account.getPassword())).isTrue();
        verify(adminAccountRepository).save(account);
    }
}