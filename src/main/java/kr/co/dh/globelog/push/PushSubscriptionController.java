package kr.co.dh.globelog.push;

import kr.co.dh.globelog.domain.PushSubscription;
import kr.co.dh.globelog.domain.PushSubscriptionRepository;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.security.CurrentUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/push")
public class PushSubscriptionController {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final VapidProperties vapidProperties;
    private final CurrentUserResolver currentUserResolver;

    public PushSubscriptionController(PushSubscriptionRepository pushSubscriptionRepository,
            VapidProperties vapidProperties, CurrentUserResolver currentUserResolver) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.vapidProperties = vapidProperties;
        this.currentUserResolver = currentUserResolver;
    }

    // 클라이언트가 pushManager.subscribe()를 호출할 때 넘겨줄 공개키. 미설정 상태면
    // 빈 문자열을 내려주고, 프론트는 이 경우 알림 켜기 UI 자체를 숨긴다.
    @GetMapping("/vapid-public-key")
    public String vapidPublicKey() {
        return vapidProperties.isConfigured() ? vapidProperties.publicKey() : "";
    }

    // 같은 endpoint로 다시 구독하는 경우(다른 계정으로 로그인 후 재구독 등)를 대비해
    // 지우고 새로 넣는 방식으로 upsert한다.
    @PostMapping("/subscribe")
    @Transactional
    public void subscribe(@RequestBody PushSubscribeRequest request, Authentication authentication) {
        User user = requireLoggedIn(authentication);
        if (request.endpoint() == null || request.endpoint().isBlank()
                || request.keys() == null || request.keys().p256dh() == null || request.keys().auth() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "구독 정보가 올바르지 않습니다.");
        }
        pushSubscriptionRepository.deleteByEndpoint(request.endpoint());
        pushSubscriptionRepository.save(
                new PushSubscription(user, request.endpoint(), request.keys().p256dh(), request.keys().auth()));
    }

    @DeleteMapping("/subscribe")
    @Transactional
    public void unsubscribe(@RequestBody PushUnsubscribeRequest request, Authentication authentication) {
        requireLoggedIn(authentication);
        pushSubscriptionRepository.deleteByEndpoint(request.endpoint());
    }

    private User requireLoggedIn(Authentication authentication) {
        return currentUserResolver.resolve(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }
}
