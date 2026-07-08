package kr.co.dh.globelog.api;

public record MeResponse(
        boolean loggedIn,
        Long id,
        String nickname,
        String profileImageUrl,
        String csrfHeaderName,
        String csrfToken) {
}
