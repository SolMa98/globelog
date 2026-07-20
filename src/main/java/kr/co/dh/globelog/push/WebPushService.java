package kr.co.dh.globelog.push;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;
import java.util.Map;
import kr.co.dh.globelog.domain.PushSubscription;
import kr.co.dh.globelog.domain.PushSubscriptionRepository;
import kr.co.dh.globelog.domain.User;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 채팅 새 메시지를 브라우저/OS 알림으로 보낸다. VAPID 키가 없으면(운영자가 아직
 * 발급 안 함) 조용히 아무 것도 안 하는 선택적 기능 — 이 서비스가 실패해도 채팅 전송
 * 자체(핵심 기능)는 절대 막히면 안 되므로, 모든 예외를 여기서 삼키고 로그만 남긴다.
 */
@Service
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final ObjectMapper objectMapper;
    private final PushService pushService;

    public WebPushService(PushSubscriptionRepository pushSubscriptionRepository, VapidProperties vapidProperties,
            ObjectMapper objectMapper) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.objectMapper = objectMapper;
        this.pushService = buildPushService(vapidProperties);
    }

    private PushService buildPushService(VapidProperties vapidProperties) {
        if (!vapidProperties.isConfigured()) {
            log.warn("VAPID_PUBLIC_KEY/VAPID_PRIVATE_KEY가 설정되지 않아 채팅 푸시 알림이 비활성화됩니다.");
            return null;
        }
        try {
            Security.addProvider(new BouncyCastleProvider());
            return new PushService(vapidProperties.publicKey(), vapidProperties.privateKey(), vapidProperties.subject());
        } catch (GeneralSecurityException e) {
            log.error("VAPID 키로 PushService를 초기화하지 못했습니다 — 채팅 푸시 알림이 비활성화됩니다.", e);
            return null;
        }
    }

    @Transactional
    public void notify(User recipient, String title, String body, String url) {
        if (pushService == null) {
            return;
        }
        List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUserId(recipient.getId());
        if (subscriptions.isEmpty()) {
            return;
        }
        String payload = buildPayload(title, body, url);
        for (PushSubscription subscription : subscriptions) {
            send(subscription, payload);
        }
    }

    private void send(PushSubscription subscription, String payload) {
        try {
            Notification notification = new Notification(
                    subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuth(), payload);
            // RFC 8291(aes128gcm)이 현재 표준 — 이 라이브러리의 기본 인코딩이 구형(aesgcm)일
            // 경우 최신 FCM/브라우저 푸시 엔드포인트가 403으로 거부하는 걸 실측으로 확인해서
            // 명시적으로 지정한다.
            HttpResponse response = pushService.send(notification, Encoding.AES128GCM);
            int status = response.getStatusLine().getStatusCode();
            // 404/410 = 브라우저가 구독을 이미 폐기함(재설치, 알림 꺼짐 등) — 재시도해봐야
            // 계속 실패하므로 이 기회에 지운다.
            if (status == 404 || status == 410) {
                pushSubscriptionRepository.deleteByEndpoint(subscription.getEndpoint());
            } else if (status >= 300) {
                log.warn("푸시 발송이 실패 응답을 받았습니다 (endpoint={}): HTTP {}", subscription.getEndpoint(), status);
            }
        } catch (Exception e) {
            log.warn("푸시 알림 발송 실패 (endpoint={}): {}", subscription.getEndpoint(), e.getMessage());
        }
    }

    private String buildPayload(String title, String body, String url) {
        return objectMapper.writeValueAsString(Map.of("title", title, "body", body, "url", url));
    }
}
