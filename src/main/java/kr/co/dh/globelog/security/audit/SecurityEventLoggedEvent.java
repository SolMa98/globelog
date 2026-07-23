package kr.co.dh.globelog.security.audit;

import java.time.LocalDateTime;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventType;

/**
 * SecurityAuditService가 발행하고 SecurityEventLogListener가 비동기로 받아 DB에 적재하는
 * 스프링 애플리케이션 이벤트. IP/User-Agent는 요청을 처리하던 스레드에서 미리 뽑아서
 * 담아둔다 — 리스너가 실행되는 비동기 스레드에는 원본 HttpServletRequest가 없기 때문
 * (RequestContextHolder가 스레드로컬 기반이라 스레드가 바뀌면 못 읽음).
 */
public record SecurityEventLoggedEvent(
        LocalDateTime occurredAt,
        SecurityEventType eventType,
        SecurityActorType actorType,
        Long actorId,
        String actorLabel,
        String targetType,
        Long targetId,
        String detail,
        String ipAddress,
        String userAgent) {
}
