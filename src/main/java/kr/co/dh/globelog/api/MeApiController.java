package kr.co.dh.globelog.api;

import kr.co.dh.globelog.domain.TripRepository;
import kr.co.dh.globelog.security.CurrentUserResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeApiController {

    private final CurrentUserResolver currentUserResolver;
    private final TripRepository tripRepository;

    public MeApiController(CurrentUserResolver currentUserResolver, TripRepository tripRepository) {
        this.currentUserResolver = currentUserResolver;
        this.tripRepository = tripRepository;
    }

    // CsrfToken을 파라미터로 받아 실제로 참조해야 CsrfFilter가 토큰을 로드해서
    // XSRF-TOKEN 쿠키를 확실히 내려준다(안 쓰면 지연 로딩이라 쿠키가 안 생길 수 있음).
    // index.html이 정적 파일이라 서버가 폼에 CSRF 값을 못 심어주니, 로그아웃 등
    // POST 요청을 할 순수 JS(fetch)가 이 응답에서 토큰 값을 바로 읽어 쓰게 한다.
    @GetMapping("/api/me")
    public MeResponse me(Authentication authentication, CsrfToken csrfToken) {
        String headerName = csrfToken.getHeaderName();
        String token = csrfToken.getToken();
        return currentUserResolver.resolve(authentication)
                .map(u -> new MeResponse(true, u.getId(), u.getNickname(), u.getProfileImageUrl(),
                        tripRepository.countDistinctCountryByUserId(u.getId()), headerName, token))
                .orElseGet(() -> new MeResponse(false, null, null, null, null, headerName, token));
    }
}
