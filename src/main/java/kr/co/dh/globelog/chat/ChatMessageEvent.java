package kr.co.dh.globelog.chat;

// WebSocket 브로드캐스트 봉투 — 새 메시지/수정/삭제를 클라이언트가 구분해서 처리할 수 있게
// type을 같이 실어보낸다(이전엔 ChatMessageResponse만 그대로 보내서 전부 "새 메시지"로만
// 처리했음).
public record ChatMessageEvent(String type, ChatMessageResponse message) {

    public static final String NEW = "NEW";
    public static final String EDIT = "EDIT";
    public static final String DELETE = "DELETE";

    public static ChatMessageEvent of(String type, ChatMessageResponse message) {
        return new ChatMessageEvent(type, message);
    }
}
