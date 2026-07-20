package kr.co.dh.globelog.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web Push(VAPID) 키 설정. publicKey/privateKey가 비어있으면 WebPushService가 발송을
 * 조용히 건너뛴다(선택적 연동 — PortOne/Gmail과 동일한 절충, README 참고).
 */
@ConfigurationProperties(prefix = "push.vapid")
public record VapidProperties(String publicKey, String privateKey, String subject) {

    public boolean isConfigured() {
        return publicKey != null && !publicKey.isBlank() && privateKey != null && !privateKey.isBlank();
    }
}
