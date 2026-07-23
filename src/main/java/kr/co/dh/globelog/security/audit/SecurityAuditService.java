package kr.co.dh.globelog.security.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 관리자 "보안 로그" 화면에 남는 감사 이벤트를 기록하는 공용 진입점. 로그인/로그아웃,
 * 게시글(Trip) CRUD/조회, 채팅 이벤트를 호출하는 쪽 어디서든 이 서비스 하나만 쓰면 된다.
 *
 * 실제 DB 저장은 SecurityEventLogListener가 비동기로 처리한다(record() 자체는 이벤트만
 * 발행하고 바로 리턴) — 채팅 메시지 전송, 게시글 조회처럼 사용자 요청 경로에서 자주
 * 호출되는 지점에 감사 로그 INSERT가 지연을 더하지 않게 하기 위함. IP/User-Agent는
 * 여기(호출 스레드)에서 미리 꺼내서 이벤트에 담는다.
 *
 * HttpServletRequest를 파라미터로 받지 않는다 — 호출부(컨트롤러/서비스) 시그니처를
 * 건드리지 않기 위해 RequestContextHolder로 현재 요청을 opportunistic하게 찾는다.
 * WebSocket(STOMP) 메시지 처리 스레드처럼 서블릿 요청 컨텍스트 밖에서 호출되면
 * ip/userAgent는 그냥 null로 남는다(알려진 한계 — 로그인/게시글/방 생성·초대·나가기는
 * 전부 일반 HTTP 요청 경로라 영향 없고, 메시지 전송/수정/삭제만 해당).
 */
@Service
public class SecurityAuditService {

    private static final int USER_AGENT_MAX_LENGTH = 255;
    private static final int DETAIL_MAX_LENGTH = 200;

    private final ApplicationEventPublisher eventPublisher;

    public SecurityAuditService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void record(SecurityEventType eventType, SecurityActorType actorType, Long actorId, String actorLabel,
            String targetType, Long targetId, String detail) {
        String ipAddress = null;
        String userAgent = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            ipAddress = request.getRemoteAddr();
            userAgent = truncate(request.getHeader("User-Agent"), USER_AGENT_MAX_LENGTH);
        }
        eventPublisher.publishEvent(new SecurityEventLoggedEvent(
                LocalDateTime.now(), eventType, actorType, actorId, actorLabel,
                targetType, targetId, truncate(detail, DETAIL_MAX_LENGTH), ipAddress, userAgent));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
