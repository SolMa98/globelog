package kr.co.dh.globelog.domain;

/**
 * 보안 감사 로그(SecurityEventLog) 화면 필터용 상위 분류. 세부 이벤트는 SecurityEventType 참고.
 */
public enum SecurityEventCategory {
    AUTH, TRIP, CHAT,
    // 엑셀 다운로드 등 특정 도메인(TRIP/CHAT)에 속하지 않는 관리자 시스템 조작.
    SYSTEM
}
