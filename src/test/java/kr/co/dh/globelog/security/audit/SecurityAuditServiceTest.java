package kr.co.dh.globelog.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * record()가 현재 요청이 있으면 IP/User-Agent를 캡처해서 이벤트에 담고, 없으면(WebSocket
 * STOMP 메시지 처리 스레드처럼 서블릿 요청 컨텍스트 밖) null로 남긴다는 계약을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 현재_요청이_있으면_IP와_UserAgent를_캡처한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.5");
        request.addHeader("User-Agent", "TestAgent/1.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        SecurityAuditService service = new SecurityAuditService(eventPublisher);
        service.record(SecurityEventType.LOGIN_SUCCESS, SecurityActorType.USER, 1L, "tester", null, null, null);

        ArgumentCaptor<SecurityEventLoggedEvent> captor = ArgumentCaptor.forClass(SecurityEventLoggedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().ipAddress()).isEqualTo("203.0.113.5");
        assertThat(captor.getValue().userAgent()).isEqualTo("TestAgent/1.0");
        assertThat(captor.getValue().actorLabel()).isEqualTo("tester");
    }

    @Test
    void 요청_컨텍스트가_없으면_IP와_UserAgent는_null이다() {
        RequestContextHolder.resetRequestAttributes();

        SecurityAuditService service = new SecurityAuditService(eventPublisher);
        service.record(SecurityEventType.CHAT_MESSAGE_SEND, SecurityActorType.USER, 1L, "tester",
                "CHAT_MESSAGE", 10L, null);

        ArgumentCaptor<SecurityEventLoggedEvent> captor = ArgumentCaptor.forClass(SecurityEventLoggedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().ipAddress()).isNull();
        assertThat(captor.getValue().userAgent()).isNull();
    }

    @Test
    void detail이_200자를_넘으면_잘라서_담는다() {
        SecurityAuditService service = new SecurityAuditService(eventPublisher);
        String longDetail = "a".repeat(250);
        service.record(SecurityEventType.TRIP_VIEW, SecurityActorType.ANONYMOUS, null, "비로그인 방문자",
                "TRIP", 1L, longDetail);

        ArgumentCaptor<SecurityEventLoggedEvent> captor = ArgumentCaptor.forClass(SecurityEventLoggedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().detail()).hasSize(200);
    }
}
