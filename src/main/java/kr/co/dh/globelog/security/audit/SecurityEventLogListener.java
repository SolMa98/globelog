package kr.co.dh.globelog.security.audit;

import kr.co.dh.globelog.domain.SecurityEventLog;
import kr.co.dh.globelog.domain.SecurityEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * SecurityAuditService.record()가 발행한 이벤트를 받아 실제로 DB에 적재한다. @Async라
 * 호출 스레드(로그인 처리, 게시글 저장, 채팅 메시지 전송 등)를 막지 않고, 여기서 예외가
 * 나도(DB 순간 장애 등) 호출 스레드로 전파되지 않는다 — 감사 로그 기록 실패가 본 기능
 * 실패로 이어지면 안 되기 때문에 의도적으로 흡수하고 경고만 남긴다.
 */
@Component
public class SecurityEventLogListener {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventLogListener.class);

    private final SecurityEventLogRepository securityEventLogRepository;

    public SecurityEventLogListener(SecurityEventLogRepository securityEventLogRepository) {
        this.securityEventLogRepository = securityEventLogRepository;
    }

    @Async
    @EventListener
    public void onSecurityEventLogged(SecurityEventLoggedEvent event) {
        try {
            securityEventLogRepository.save(new SecurityEventLog(
                    event.occurredAt(), event.eventType(), event.actorType(), event.actorId(), event.actorLabel(),
                    event.targetType(), event.targetId(), event.detail(), event.ipAddress(), event.userAgent()));
        } catch (Exception e) {
            log.warn("보안 로그 기록 실패: eventType={}, actorLabel={}", event.eventType(), event.actorLabel(), e);
        }
    }
}
