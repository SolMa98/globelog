package kr.co.dh.globelog.domain;

/**
 * 공개 피드 노출 여부. 다중 사용자 전환 전 데이터(관리자가 백오피스로 등록한 여행)는
 * 전부 PUBLIC이 기본값 — 애초에 이 앱 자체가 처음부터 완전 공개였으므로 자연스러운 기본값.
 *
 * FOLLOWERS_ONLY("친구공개")는 양방향 친구가 아니라 일방향 팔로우(Follow) 기준 —
 * 글쓴이(followee)를 팔로우하는 사람에게만 노출된다.
 */
public enum TripVisibility {
    PUBLIC,
    FOLLOWERS_ONLY,
    PRIVATE
}
