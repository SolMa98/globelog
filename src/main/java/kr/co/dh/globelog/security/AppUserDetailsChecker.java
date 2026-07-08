package kr.co.dh.globelog.security;

import kr.co.dh.globelog.domain.UserRepository;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * DaoAuthenticationProvider의 preAuthenticationChecks로 등록해서, 비밀번호 검증
 * 이전에 (1) 기본 계정 상태(비활성/잠김) + (2) 이메일 인증 여부를 함께 확인한다.
 */
@Component
public class AppUserDetailsChecker implements UserDetailsChecker {

    private final AccountStatusUserDetailsChecker defaultChecker = new AccountStatusUserDetailsChecker();
    private final UserRepository userRepository;

    public AppUserDetailsChecker(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void check(UserDetails toCheck) {
        defaultChecker.check(toCheck);

        kr.co.dh.globelog.domain.User user = userRepository.findByEmail(toCheck.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 계정입니다: " + toCheck.getUsername()));
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException("이메일 인증이 필요합니다.");
        }
    }
}
