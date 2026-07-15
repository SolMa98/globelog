package kr.co.dh.globelog.api;

public record MeResponse(
        boolean loggedIn,
        Long id,
        String nickname,
        String profileImageUrl,
        Long visitedCountryCount,
        long totalCountryCount,
        String csrfHeaderName,
        String csrfToken) {
}
