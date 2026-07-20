package kr.co.dh.globelog.chat;

import java.security.Principal;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ChatWebSocketController {

    private final ChatMessageService chatMessageService;
    private final UserRepository userRepository;

    public ChatWebSocketController(ChatMessageService chatMessageService, UserRepository userRepository) {
        this.chatMessageService = chatMessageService;
        this.userRepository = userRepository;
    }

    // 클라이언트가 STOMP CONNECT할 때 핸드셰이크(HTTP 세션)에서 넘어온 Principal이 그대로
    // 붙는다 — 별도 인증 로직 없이 로그인 세션을 재사용.
    @MessageMapping("/chat.send")
    public void send(@Payload ChatSendTextRequest request, Principal principal) {
        User sender = resolveUser(principal);
        chatMessageService.sendText(request.roomId(), sender, request.content());
    }

    private User resolveUser(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }
}
