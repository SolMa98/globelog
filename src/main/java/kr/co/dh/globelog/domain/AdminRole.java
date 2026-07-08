package kr.co.dh.globelog.domain;

public enum AdminRole {
    /** 시스템 전권 — 계정 관리, 여행(개인정보) 열람, 통계, 우선순위 설정 전부 가능 */
    SUPER_ADMIN,
    /** 콘텐츠 모더레이터 — 국가/지역 마스터 데이터만 관리, 개인 여행 데이터·계정 관리·통계는 접근 불가 */
    MODERATOR
}