package kr.co.dh.globelog.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import kr.co.dh.globelog.domain.ChatMessageRepository;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventLogRepository;
import kr.co.dh.globelog.domain.SecurityEventType;
import kr.co.dh.globelog.domain.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 관리자(compute, 전체 합산)와 개인(computeForUser, 본인 필터) 두 경로가 서로 다른
 * 레포지토리 메서드로 갈라지는지 검증한다 — 특히 게시글 삭제 건수는 SecurityEventLog에서만
 * 나오므로(Trip은 hard delete라 현재 상태로는 못 셈) 개인 조회 시 actorType=USER +
 * actorId 필터가 정확히 걸리는지가 핵심.
 */
@ExtendWith(MockitoExtension.class)
class ActivityStatsServiceTest {

    @Mock
    private TripRepository tripRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private SecurityEventLogRepository securityEventLogRepository;

    @Test
    void compute는_전체_합산_레포지토리_메서드를_쓴다() {
        when(tripRepository.sumViewCount()).thenReturn(100L);
        when(chatMessageRepository.count()).thenReturn(50L);
        when(securityEventLogRepository.countByEventType(SecurityEventType.TRIP_CREATE)).thenReturn(10L);
        when(securityEventLogRepository.countByEventType(SecurityEventType.TRIP_UPDATE)).thenReturn(5L);
        when(securityEventLogRepository.countByEventType(SecurityEventType.TRIP_DELETE)).thenReturn(2L);

        ActivityStatsService service =
                new ActivityStatsService(tripRepository, chatMessageRepository, securityEventLogRepository);
        ActivityStatsResponse result = service.compute();

        assertThat(result.viewCount()).isEqualTo(100L);
        assertThat(result.chatMessageCount()).isEqualTo(50L);
        assertThat(result.tripCreateCount()).isEqualTo(10L);
        assertThat(result.tripUpdateCount()).isEqualTo(5L);
        assertThat(result.tripDeleteCount()).isEqualTo(2L);
    }

    @Test
    void computeForUser는_본인_기준으로만_필터링한다() {
        Long userId = 42L;
        when(tripRepository.sumViewCountByUserId(userId)).thenReturn(7L);
        when(chatMessageRepository.countBySenderId(userId)).thenReturn(3L);
        when(securityEventLogRepository.countByEventTypeAndActorTypeAndActorId(
                SecurityEventType.TRIP_CREATE, SecurityActorType.USER, userId)).thenReturn(4L);
        when(securityEventLogRepository.countByEventTypeAndActorTypeAndActorId(
                SecurityEventType.TRIP_UPDATE, SecurityActorType.USER, userId)).thenReturn(1L);
        when(securityEventLogRepository.countByEventTypeAndActorTypeAndActorId(
                SecurityEventType.TRIP_DELETE, SecurityActorType.USER, userId)).thenReturn(0L);

        ActivityStatsService service =
                new ActivityStatsService(tripRepository, chatMessageRepository, securityEventLogRepository);
        ActivityStatsResponse result = service.computeForUser(userId);

        assertThat(result.viewCount()).isEqualTo(7L);
        assertThat(result.chatMessageCount()).isEqualTo(3L);
        assertThat(result.tripCreateCount()).isEqualTo(4L);
        assertThat(result.tripUpdateCount()).isEqualTo(1L);
        assertThat(result.tripDeleteCount()).isEqualTo(0L);
    }
}
