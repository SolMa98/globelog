package kr.co.dh.globelog.chat;

// WebSocket(STOMP)으로 텍스트 메시지를 보낼 때의 페이로드. roomId를 목적지 경로가 아니라
// 페이로드에 실어보내므로, 방 멤버십 검증은 서비스 계층(ChatMessageService)에서 한다.
public record ChatSendTextRequest(Long roomId, String content) {
}
