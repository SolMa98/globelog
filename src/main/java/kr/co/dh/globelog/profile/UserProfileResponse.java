package kr.co.dh.globelog.profile;

public record UserProfileResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        String bio,
        long followerCount,
        long followingCount,
        boolean isFollowing,
        boolean isSelf) {
}
