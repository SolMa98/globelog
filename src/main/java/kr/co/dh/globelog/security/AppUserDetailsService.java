package kr.co.dh.globelog.security;

import kr.co.dh.globelog.domain.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 일반 가입 사용자(kr.co.dh.globelog.domain.User) 로그인용. 관리자 백오피스
 * (AdminUserDetailsService/AdminAccount)와는 완전히 분리된 인증 영역 — 이름이
 * 겹치는 도메인 엔티티는 FQCN(kr.co.dh.globelog.domain.User)으로 구분해서 사용.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        kr.co.dh.globelog.domain.User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 계정입니다: " + email));

        return User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .disabled(!user.isEnabled())
                .roles("USER")
                .build();
    }
}
