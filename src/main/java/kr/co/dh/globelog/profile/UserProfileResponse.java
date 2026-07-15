package kr.co.dh.globelog.profile;

public record UserProfileResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        String bio,
        long followerCount,
        long followingCount,
        long tripCount,
        long visitedCountryCount,
        int joinYear,
        boolean isFollowing,
        boolean isSelf) {
}
