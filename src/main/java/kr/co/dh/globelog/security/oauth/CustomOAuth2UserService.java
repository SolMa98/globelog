package kr.co.dh.globelog.security.oauth;

import java.util.List;
import java.util.Map;
import kr.co.dh.globelog.domain.SocialAccount;
import kr.co.dh.globelog.domain.SocialAccountRepository;
import kr.co.dh.globelog.domain.SocialProvider;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Google/Naver/Kakao 로그인 응답을 도메인 User와 연결하는 어댑터.
 *
 * 계정 매칭은 항상 provider+providerId(SocialAccount) 기준이고, 이메일은 "검증된 경우에만"
 * 자동 연동/승격 판단에 쓴다 — project_multiuser_design_discussion 메모리에 정리된 정책 그대로.
 * 신규 가입(닉네임 미정)인 경우 DB에는 아무것도 쓰지 않고, ROLE_PRE_SIGNUP 권한만 가진
 * 임시 OAuth2User를 반환해 SocialSignupController(/signup/social/complete)에서 이어받는다.
 */
@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    public static final String PRE_SIGNUP_AUTHORITY = "ROLE_PRE_SIGNUP";

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    public CustomOAuth2UserService(UserRepository userRepository, SocialAccountRepository socialAccountRepository) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        SocialProvider provider = toProvider(registrationId);
        SocialUserInfo info = extractUserInfo(provider, oauth2User.getAttributes());

        if (info.email() == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_verified"),
                    "검증된 이메일 정보를 제공하지 않아 가입할 수 없습니다.");
        }

        return socialAccountRepository.findByProviderAndProviderId(provider, info.providerId())
                .map(SocialAccount::getUser)
                .map(this::userPrincipal)
                .orElseGet(() -> linkOrCreatePrincipal(provider, info));
    }

    private OAuth2User linkOrCreatePrincipal(SocialProvider provider, SocialUserInfo info) {
        return userRepository.findByEmail(info.email())
                .map(user -> {
                    if (!user.isEmailVerified()) {
                        // 소셜 제공자의 검증을 우리 메일 인증과 최소 동급으로 인정해 승격한다.
                        user.setEmailVerified(true);
                        userRepository.save(user);
                    }
                    socialAccountRepository.save(new SocialAccount(user, provider, info.providerId()));
                    return userPrincipal(user);
                })
                .orElseGet(() -> preSignupPrincipal(provider, info));
    }

    /** 가입 완료(SocialSignupController) 이후 정식 principal을 새로 만들 때도 재사용. */
    public OAuth2User userPrincipal(User user) {
        // getName()이 user.getEmail()을 반환하도록 해서, CurrentUserResolver의 기존
        // "Authentication.getName() == email" 조회 로직을 폼로그인/소셜로그인 모두 그대로 재사용한다.
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", user.getEmail()),
                "email");
    }

    private OAuth2User preSignupPrincipal(SocialProvider provider, SocialUserInfo info) {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority(PRE_SIGNUP_AUTHORITY)),
                Map.of(
                        "email", info.email(),
                        "provider", provider.name(),
                        "providerId", info.providerId()),
                "email");
    }

    private SocialProvider toProvider(String registrationId) {
        return switch (registrationId) {
            case "google" -> SocialProvider.GOOGLE;
            case "naver" -> SocialProvider.NAVER;
            case "kakao" -> SocialProvider.KAKAO;
            default -> throw new OAuth2AuthenticationException("지원하지 않는 로그인 제공자입니다: " + registrationId);
        };
    }

    @SuppressWarnings("unchecked")
    private SocialUserInfo extractUserInfo(SocialProvider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case GOOGLE -> {
                String providerId = String.valueOf(attributes.get("sub"));
                boolean verified = Boolean.TRUE.equals(attributes.get("email_verified"));
                String email = verified ? (String) attributes.get("email") : null;
                yield new SocialUserInfo(providerId, email);
            }
            case NAVER -> {
                Map<String, Object> response = (Map<String, Object>) attributes.get("response");
                String providerId = String.valueOf(response.get("id"));
                // 네이버는 별도 검증 플래그를 내려주지 않음 — 이메일 등록 자체가 본인확인을
                // 거친다는 네이버 정책을 근거로 제공된 이메일을 검증된 것으로 간주한다.
                String email = (String) response.get("email");
                yield new SocialUserInfo(providerId, email);
            }
            case KAKAO -> {
                String providerId = String.valueOf(attributes.get("id"));
                Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                boolean verified = kakaoAccount != null
                        && Boolean.TRUE.equals(kakaoAccount.get("is_email_valid"))
                        && Boolean.TRUE.equals(kakaoAccount.get("is_email_verified"));
                String email = verified ? (String) kakaoAccount.get("email") : null;
                yield new SocialUserInfo(providerId, email);
            }
        };
    }
}
