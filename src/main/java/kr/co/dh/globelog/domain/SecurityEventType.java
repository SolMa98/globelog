package kr.co.dh.globelog.domain;

/**
 * 보안 감사 로그(SecurityEventLog)에 남는 이벤트 종류. 각 상수가 화면 필터/배지에 쓰는
 * 카테고리와 한글 라벨을 함께 갖고 있어, 화면 쪽에서 별도 매핑 테이블을 두지 않아도 된다.
 */
public enum SecurityEventType {
    LOGIN_SUCCESS(SecurityEventCategory.AUTH, "로그인 성공"),
    LOGIN_FAILURE(SecurityEventCategory.AUTH, "로그인 실패"),
    LOGOUT(SecurityEventCategory.AUTH, "로그아웃"),

    TRIP_CREATE(SecurityEventCategory.TRIP, "게시글 등록"),
    TRIP_UPDATE(SecurityEventCategory.TRIP, "게시글 수정"),
    TRIP_DELETE(SecurityEventCategory.TRIP, "게시글 삭제"),
    TRIP_VIEW(SecurityEventCategory.TRIP, "게시글 조회"),

    CHAT_ROOM_CREATE(SecurityEventCategory.CHAT, "채팅방 생성"),
    CHAT_INVITE(SecurityEventCategory.CHAT, "채팅방 초대"),
    CHAT_LEAVE(SecurityEventCategory.CHAT, "채팅방 나가기"),
    CHAT_MESSAGE_SEND(SecurityEventCategory.CHAT, "메시지 전송"),
    CHAT_MESSAGE_EDIT(SecurityEventCategory.CHAT, "메시지 수정"),
    CHAT_MESSAGE_DELETE(SecurityEventCategory.CHAT, "메시지 삭제");

    private final SecurityEventCategory category;
    private final String label;

    SecurityEventType(SecurityEventCategory category, String label) {
        this.category = category;
        this.label = label;
    }

    public SecurityEventCategory getCategory() {
        return category;
    }

    public String getLabel() {
        return label;
    }
}
