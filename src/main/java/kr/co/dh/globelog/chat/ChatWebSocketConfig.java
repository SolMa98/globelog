package kr.co.dh.globelog.chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * SockJS 없이 순수 WebSocket만 쓴다 — 최신 브라우저는 전부 native WebSocket을 지원하고,
 * SockJS 폴백(long-polling 등)까지 구현/검증할 만큼의 이득이 이 프로젝트 규모에선 없음.
 * 인증은 별도 핸드셰이크 로직 없이 기존 세션 쿠키 기반 Spring Security를 그대로 탄다
 * (SecurityConfig에서 /ws-chat/**을 인증 필요로 막아두면, 핸드셰이크 시점의
 * HttpServletRequest#getUserPrincipal()이 STOMP 세션의 Principal로 그대로 넘어옴).
 */
@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatChannelInterceptor chatChannelInterceptor;

    public ChatWebSocketConfig(ChatChannelInterceptor chatChannelInterceptor) {
        this.chatChannelInterceptor = chatChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(chatChannelInterceptor);
    }
}
