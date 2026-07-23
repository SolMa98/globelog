package kr.co.dh.globelog.admin;

/**
 * 통계 화면(/my/stats, /admin/stats)의 "활동 통계" 카드용. 등록/수정/삭제 건수는
 * SecurityEventLog 기반이라 이미 삭제된 게시글에 대한 건수도 포함된다(ActivityStatsService 참고).
 */
public record ActivityStatsResponse(
        long viewCount,
        long chatMessageCount,
        long tripCreateCount,
        long tripUpdateCount,
        long tripDeleteCount) {
}
