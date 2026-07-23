package kr.co.dh.globelog.security.audit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventLog;
import kr.co.dh.globelog.domain.SecurityEventLogRepository;
import kr.co.dh.globelog.domain.SecurityEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 감사 로그 기록 실패(DB 순간 장애 등)가 호출 스레드(로그인/게시글 저장/채팅 메시지 전송)로
 * 전파되면 안 된다는 계약을 검증한다 — @Async 리스너가 예외를 흡수하고 경고만 남겨야 함.
 */
@ExtendWith(MockitoExtension.class)
class SecurityEventLogListenerTest {

    @Mock
    private SecurityEventLogRepository securityEventLogRepository;

    private SecurityEventLoggedEvent sampleEvent() {
        return new SecurityEventLoggedEvent(LocalDateTime.now(), SecurityEventType.LOGIN_SUCCESS,
                SecurityActorType.USER, 1L, "tester", null, null, null, "127.0.0.1", "TestAgent");
    }

    @Test
    void 정상적으로_이벤트를_받으면_레포지토리에_저장한다() {
        SecurityEventLogListener listener = new SecurityEventLogListener(securityEventLogRepository);
        listener.onSecurityEventLogged(sampleEvent());
        verify(securityEventLogRepository).save(any(SecurityEventLog.class));
    }

    @Test
    void 저장이_실패해도_예외를_던지지_않는다() {
        when(securityEventLogRepository.save(any())).thenThrow(new RuntimeException("DB 순간 장애"));
        SecurityEventLogListener listener = new SecurityEventLogListener(securityEventLogRepository);

        assertThatCode(() -> listener.onSecurityEventLogged(sampleEvent())).doesNotThrowAnyException();
    }
}
