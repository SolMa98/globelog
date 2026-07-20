package kr.co.dh.globelog.security;

import kr.co.dh.globelog.domain.AdminIpWhitelistEntryRepository;
import kr.co.dh.globelog.security.oauth.CustomOAuth2UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * 인증 영역이 두 개라 SecurityFilterChain도 두 개로 분리한다:
 * - adminSecurityFilterChain: /admin/** 전용, AdminAccount(관리자 백오피스) 로그인
 * - appSecurityFilterChain: 나머지 전부, User(일반 가입 사용자) 로그인
 * 두 체인이 서로 다른 UserDetailsService를 쓰므로, Spring Boot가 자동으로 하나의
 * AuthenticationManager로 뒤섞지 않도록 각 체인에 DaoAuthenticationProvider를
 * 명시적으로 붙여준다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // TwoFactorAndOnboardingSuccessHandler/TwoFactorController/SocialSignupController가
    // 세션에 저장된 인증 상태를 직접 지우거나 다시 써야 해서(2FA 게이트, 온보딩 완료 후 전환)
    // 필터 체인이 쓰는 것과 동일한 리포지토리를 빈으로 공유한다.
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            AdminUserDetailsService adminUserDetailsService,
            PasswordEncoder passwordEncoder,
            AdminIpWhitelistEntryRepository adminIpWhitelistEntryRepository,
            @Value("${app.admin.ip-whitelist.enabled:false}") boolean adminIpWhitelistEnabled)
            throws Exception {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(adminUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        http
                .securityMatcher("/admin/**")
                .authenticationProvider(provider)
                // 로그인 시도 자체가 처리되기 전에 IP부터 걸러낸다(꺼져 있으면 통과만 시킴).
                .addFilterBefore(
                        new AdminIpWhitelistFilter(adminIpWhitelistEntryRepository, adminIpWhitelistEnabled),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers("/admin/login").permitAll()
                        // 계정 관리·개인 여행 데이터 열람·통계·IP 화이트리스트는 최상위 관리자 전용.
                        // 모더레이터는 국가/지역 마스터 데이터 관리만 가능(아래 anyRequest로 허용).
                        .requestMatchers("/admin/accounts/**", "/admin/trips/**", "/admin/stats/**", "/admin/ip-whitelist/**")
                        .hasRole("SUPER_ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .defaultSuccessUrl("/admin/countries", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login")
                        .permitAll());

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain appSecurityFilterChain(
            HttpSecurity http,
            AppUserDetailsService appUserDetailsService,
            PasswordEncoder passwordEncoder,
            AppUserDetailsChecker appUserDetailsChecker,
            LoginFailureHandler loginFailureHandler,
            CustomOAuth2UserService customOAuth2UserService,
            TwoFactorAndOnboardingSuccessHandler twoFactorAndOnboardingSuccessHandler,
            SecurityContextRepository securityContextRepository)
            throws Exception {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(appUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // 이메일 미인증 계정을 비밀번호 검증 이전에 걸러낸다 — AppUserDetailsChecker 참고.
        provider.setPreAuthenticationChecks(appUserDetailsChecker);

        http
                .authenticationProvider(provider)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                // index.html이 Thymeleaf가 아니라 정적 파일이라 서버가 폼에 CSRF 히든
                // 필드를 못 넣어준다. 쿠키로 토큰을 내려서 순수 JS(fetch)가 읽어
                // X-XSRF-TOKEN 헤더로 실어보낼 수 있게 함(Spring Security 표준 SPA 패턴).
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .authorizeHttpRequests(registry -> registry
                        // 채팅은 전부 로그인 사용자 전용이라(비로그인 열람 불가) /api/** 공통
                        // permitAll보다 먼저 매칭되게 위에 둔다. WebSocket 핸드셰이크(/ws-chat)도
                        // 같은 세션 인증을 타야 STOMP Principal이 채워진다.
                        .requestMatchers("/ws-chat/**", "/api/chat/**", "/api/push/**").authenticated()
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/data/**", "/uploads/**", "/api/**").permitAll()
                        .requestMatchers("/login", "/signup", "/verify-email", "/login/2fa/**", "/signup/social/**")
                        .permitAll()
                        // 셀프서비스 여행 CRUD 화면 — 본인 로그인 필요(소유권 체크는 컨트롤러가 한번 더 함)
                        .requestMatchers("/my/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(twoFactorAndOnboardingSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(twoFactorAndOnboardingSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .permitAll());

        return http.build();
    }
}
