package kr.co.dh.globelog.chat;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.co.dh.globelog.domain.ChatRoomMemberRepository;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * STOMP SUBSCRIBE 시점에 "/topic/chat.{roomId}"를 그 방 멤버만 구독할 수 있게 막는다.
 * SEND는 목적지가 "/app/chat.send"라 페이로드 안의 roomId를 여기서 볼 수 없으므로,
 * 그쪽 멤버십 검증은 ChatMessageService에서 한다(둘이 합쳐서 구독/전송 양쪽 다 커버됨).
 * 겸사겸사 SUBSCRIBE/UNSUBSCRIBE 시점에 ChatPresenceService에 "지금 이 방을 보고
 * 있음"을 기록해서, 새 메시지가 왔을 때 이미 보고 있는 사람한테는 푸시 알림을
 * 중복으로 안 보내게 한다.
 */
@Component
public class ChatChannelInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/chat\\.(\\d+)$");

    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;
    private final ChatPresenceService chatPresenceService;

    public ChatChannelInterceptor(ChatRoomMemberRepository chatRoomMemberRepository, UserRepository userRepository,
            ChatPresenceService chatPresenceService) {
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.userRepository = userRepository;
        this.chatPresenceService = chatPresenceService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            Matcher matcher = destination != null ? ROOM_TOPIC.matcher(destination) : null;
            if (matcher != null && matcher.matches()) {
                Long roomId = Long.valueOf(matcher.group(1));
                Long userId = resolveUserId(accessor.getUser());
                if (userId == null || !chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
                    throw new MessageDeliveryException("참여 중인 대화방이 아닙니다: " + roomId);
                }
                chatPresenceService.onSubscribe(accessor.getSessionId(), accessor.getSubscriptionId(), roomId, userId);
            }
        } else if (accessor.getCommand() == StompCommand.UNSUBSCRIBE) {
            chatPresenceService.onUnsubscribe(accessor.getSessionId(), accessor.getSubscriptionId());
        }
        return message;
    }

    private Long resolveUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userRepository.findByEmail(principal.getName()).map(User::getId).orElse(null);
    }
}
