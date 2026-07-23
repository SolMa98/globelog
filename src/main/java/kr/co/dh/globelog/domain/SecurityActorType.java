package kr.co.dh.globelog.domain;

/**
 * 보안 감사 로그의 행위 주체 구분. ADMIN(AdminAccount)과 USER(kr.co.dh.globelog.domain.User)는
 * 완전히 분리된 인증 영역이라(SecurityConfig 참고) 로그에서도 항상 이 값으로 구분해서 보여준다.
 * ANONYMOUS는 비로그인 방문자가 공개 게시글을 조회한 경우에만 쓰인다.
 */
public enum SecurityActorType {
    ADMIN, USER, ANONYMOUS
}
