package kr.co.dh.globelog.admin;

import kr.co.dh.globelog.domain.ChatMessageRepository;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventLogRepository;
import kr.co.dh.globelog.domain.SecurityEventType;
import kr.co.dh.globelog.domain.TripRepository;
import org.springframework.stereotype.Service;

/**
 * 통계 화면(/my/stats, /admin/stats)의 "활동 통계" 카드 — 조회수/채팅 메시지/게시글
 * 등록·수정·삭제 건수. StatsService(여행 커버리지 통계)와는 완전히 분리된 별개 서비스라
 * compute()/computeForUser() 이름은 같지만 반환 타입과 집계 방식이 다르다.
 *
 * 조회수는 Trip.viewCount 합산(현재 존재하는 게시글 기준), 등록/수정/삭제 건수는
 * SecurityEventLog 집계(Trip은 삭제 시 실제 row가 사라지는 hard delete라, 삭제된 게시글의
 * "등록/수정/삭제 이력"은 감사 로그에만 남아있음).
 */
@Service
public class ActivityStatsService {

    private final TripRepository tripRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SecurityEventLogRepository securityEventLogRepository;

    public ActivityStatsService(TripRepository tripRepository, ChatMessageRepository chatMessageRepository,
            SecurityEventLogRepository securityEventLogRepository) {
        this.tripRepository = tripRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.securityEventLogRepository = securityEventLogRepository;
    }

    // 관리자용 — 전체 사용자 합산(StatsService.compute()와 동일한 "감사용 대시보드는
    // 전체 합산" 방침, admin/security-logs와 마찬가지로 관리자 자신의 활동도 포함).
    public ActivityStatsResponse compute() {
        return new ActivityStatsResponse(
                tripRepository.sumViewCount(),
                chatMessageRepository.count(),
                securityEventLogRepository.countByEventType(SecurityEventType.TRIP_CREATE),
                securityEventLogRepository.countByEventType(SecurityEventType.TRIP_UPDATE),
                securityEventLogRepository.countByEventType(SecurityEventType.TRIP_DELETE));
    }

    // 개인 통계용 — 본인 소유/본인 행위 기준으로만 필터링.
    public ActivityStatsResponse computeForUser(Long userId) {
        return new ActivityStatsResponse(
                tripRepository.sumViewCountByUserId(userId),
                chatMessageRepository.countBySenderId(userId),
                securityEventLogRepository.countByEventTypeAndActorTypeAndActorId(
                        SecurityEventType.TRIP_CREATE, SecurityActorType.USER, userId),
                securityEventLogRepository.countByEventTypeAndActorTypeAndActorId(
                        SecurityEventType.TRIP_UPDATE, SecurityActorType.USER, userId),
                securityEventLogRepository.countByEventTypeAndActorTypeAndActorId(
                        SecurityEventType.TRIP_DELETE, SecurityActorType.USER, userId));
    }
}
