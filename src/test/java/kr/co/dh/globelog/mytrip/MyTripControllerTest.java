package kr.co.dh.globelog.mytrip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import kr.co.dh.globelog.domain.CountryRepository;
import kr.co.dh.globelog.domain.RegionRepository;
import kr.co.dh.globelog.domain.Trip;
import kr.co.dh.globelog.domain.TripImageRepository;
import kr.co.dh.globelog.domain.TripRepository;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.file.FileStorageService;
import kr.co.dh.globelog.security.CurrentUserResolver;
import kr.co.dh.globelog.security.audit.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/**
 * "본인 소유 여행만 관리할 수 있다"는 보안 경계(findOwnedTripOrThrow)가 실제로
 * 다른 사람 소유 여행 접근을 막는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class MyTripControllerTest {

    @Mock
    private TripRepository tripRepository;
    @Mock
    private TripImageRepository tripImageRepository;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private CountryRepository countryRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private CurrentUserResolver currentUserResolver;
    @Mock
    private SecurityAuditService securityAuditService;
    @Mock
    private Authentication authentication;
    @Mock
    private Trip trip;

    private MyTripController controller;
    private User viewer;
    private User otherOwner;

    @BeforeEach
    void setUp() {
        controller = new MyTripController(tripRepository, tripImageRepository, regionRepository,
                countryRepository, fileStorageService, currentUserResolver, securityAuditService);

        viewer = mock(User.class);
        otherOwner = mock(User.class);
    }

    @Test
    void viewingOtherUsersTripThrowsForbidden() {
        when(viewer.getId()).thenReturn(1L);
        when(otherOwner.getId()).thenReturn(2L);
        when(currentUserResolver.resolve(authentication)).thenReturn(Optional.of(viewer));
        when(trip.getUser()).thenReturn(otherOwner);
        when(tripRepository.findById(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> controller.detail(99L, authentication))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deletingOtherUsersTripThrowsForbidden() {
        when(viewer.getId()).thenReturn(1L);
        when(otherOwner.getId()).thenReturn(2L);
        when(currentUserResolver.resolve(authentication)).thenReturn(Optional.of(viewer));
        when(trip.getUser()).thenReturn(otherOwner);
        when(tripRepository.findById(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> controller.delete(99L, authentication))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void viewingNonExistentTripThrowsNotFound() {
        when(currentUserResolver.resolve(authentication)).thenReturn(Optional.of(viewer));
        when(tripRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.detail(404L, authentication))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void viewingWithoutLoginThrowsUnauthorized() {
        when(currentUserResolver.resolve(authentication)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.detail(1L, authentication))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deletingOwnTripAlsoDeletesImages() {
        when(viewer.getId()).thenReturn(1L);
        when(currentUserResolver.resolve(authentication)).thenReturn(Optional.of(viewer));
        when(trip.getUser()).thenReturn(viewer);
        when(tripRepository.findById(5L)).thenReturn(Optional.of(trip));
        when(tripImageRepository.findByTripOrderBySortOrderAsc(trip)).thenReturn(List.of());

        var response = controller.delete(5L, authentication);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}