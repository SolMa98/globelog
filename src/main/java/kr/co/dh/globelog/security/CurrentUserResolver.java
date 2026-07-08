package kr.co.dh.globelog.security;

import java.util.Optional;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * User(일반 가입 사용자) 인증에서 현재 로그인한 도메인 User를 안전하게 뽑아내는 헬퍼.
 * `/api/feed` 등 여러 컨트롤러가 필요로 해서 공용으로 뺌 — 클라이언트가 보낸 값을
 * 신뢰하지 않고 항상 SecurityContext의 Authentication에서만 조회한다.
 */
@Component
public class CurrentUserResolver {

    private final UserRepository userRepository;

    public CurrentUserResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> resolve(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return userRepository.findByEmail(authentication.getName());
    }
}
