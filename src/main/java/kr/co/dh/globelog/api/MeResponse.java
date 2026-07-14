package kr.co.dh.globelog.api;

public record MeResponse(
        boolean loggedIn,
        Long id,
        String nickname,
        String profileImageUrl,
        Long visitedCountryCount,
        String csrfHeaderName,
        String csrfToken) {
}
