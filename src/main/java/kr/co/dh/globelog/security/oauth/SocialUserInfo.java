package kr.co.dh.globelog.security.oauth;

/**
 * Google/Naver/Kakao 각 제공자의 서로 다른 응답 구조를 어댑팅한 결과.
 * email이 null이면 "검증된 이메일을 제공하지 않음"을 의미 — 가입/연동을 진행할 수 없다.
 */
public record SocialUserInfo(String providerId, String email) {
}
