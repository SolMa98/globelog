package kr.co.dh.globelog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// 관리자 보안 로그 화면의 필터(카테고리/주체유형/기간/키워드)가 서로 조합 가능해야 해서
// derived query 메서드 대신 Specification(AdminSecurityLogController 참고)을 쓴다.
public interface SecurityEventLogRepository
        extends JpaRepository<SecurityEventLog, Long>, JpaSpecificationExecutor<SecurityEventLog> {

    // 통계 화면의 "게시글 등록/수정/삭제 건수" 카드용 — Trip은 삭제 시 실제 row가 사라지는
    // hard delete라, 삭제 "건수" 자체는 이 감사 로그가 유일한 출처다(ActivityStatsService 참고).
    long countByEventType(SecurityEventType eventType);

    long countByEventTypeAndActorTypeAndActorId(
            SecurityEventType eventType, SecurityActorType actorType, Long actorId);
}
