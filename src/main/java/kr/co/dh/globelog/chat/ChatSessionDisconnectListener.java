package kr.co.dh.globelog.chat;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * 탭을 닫거나 네트워크가 끊기는 등 UNSUBSCRIBE 없이 세션이 통째로 끊기는 경우까지
 * ChatPresenceService의 "보고 있음" 상태를 확실히 정리하기 위한 리스너.
 */
@Component
public class ChatSessionDisconnectListener {

    private final ChatPresenceService chatPresenceService;

    public ChatSessionDisconnectListener(ChatPresenceService chatPresenceService) {
        this.chatPresenceService = chatPresenceService;
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        chatPresenceService.onSessionDisconnect(event.getSessionId());
    }
}
