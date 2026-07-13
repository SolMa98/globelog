package kr.co.dh.globelog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kr.co.dh.globelog.domain.AdminIpWhitelistEntry;
import kr.co.dh.globelog.domain.AdminIpWhitelistEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AdminIpWhitelistControllerTest {

    private AdminIpWhitelistEntryRepository repository;
    private AdminIpWhitelistController controller;

    @BeforeEach
    void setUp() {
        repository = mock(AdminIpWhitelistEntryRepository.class);
        controller = new AdminIpWhitelistController(repository);
    }

    @Test
    void 올바른_단일_IP는_저장된다() {
        ResponseEntity<?> response = controller.create("203.0.113.5", "office");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repository).save(any(AdminIpWhitelistEntry.class));
    }

    @Test
    void 올바른_CIDR도_저장된다() {
        ResponseEntity<?> response = controller.create("192.168.1.0/24", "home");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repository).save(any(AdminIpWhitelistEntry.class));
    }

    @Test
    void 형식이_잘못되면_저장하지_않고_400을_반환한다() {
        ResponseEntity<?> response = controller.create("이건-IP가-아님", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any());
    }

    @Test
    void 이미_등록된_항목은_중복_저장하지_않는다() {
        when(repository.existsByCidr("203.0.113.5")).thenReturn(true);

        ResponseEntity<?> response = controller.create("203.0.113.5", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any());
    }

    @Test
    void 삭제는_레포지토리에_위임한다() {
        controller.delete(5L);

        verify(repository).deleteById(5L);
    }
}
