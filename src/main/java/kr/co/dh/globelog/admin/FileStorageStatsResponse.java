package kr.co.dh.globelog.admin;

/**
 * 관리자 전용(/admin/stats/storage) 파일 저장 용량 통계.
 * permanent: 여행(게시글) 사진 — 보관기간 없음.
 * chatAttachment: 채팅 첨부파일 중 아직 만료 전(파일이 실제로 남아있는) 것 전체.
 * expiringSoon: chatAttachment의 부분집합 — 다음 정리 주기(3개월 컷오프)로부터 7일 안에 삭제될 것.
 */
public record FileStorageStatsResponse(
        long permanentBytes,
        long permanentCount,
        long chatAttachmentBytes,
        long chatAttachmentCount,
        long expiringSoonBytes,
        long expiringSoonCount) {
}
