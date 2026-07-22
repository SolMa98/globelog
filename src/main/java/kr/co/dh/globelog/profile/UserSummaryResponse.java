package kr.co.dh.globelog.profile;

/**
 * 사용자 검색/팔로잉 목록 등에서 쓰는 최소 정보(아바타 표시에 필요한 필드만).
 */
public record UserSummaryResponse(Long id, String nickname, String profileImageUrl) {
}