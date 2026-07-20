package kr.co.dh.globelog.chat;

import java.util.List;
import java.util.Map;
import kr.co.dh.globelog.domain.ChatRoom;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.security.CurrentUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat/rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;
    private final CurrentUserResolver currentUserResolver;

    public ChatRoomController(ChatRoomService chatRoomService, ChatMessageService chatMessageService,
            CurrentUserResolver currentUserResolver) {
        this.chatRoomService = chatRoomService;
        this.chatMessageService = chatMessageService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public List<ChatRoomSummaryResponse> list(Authentication authentication) {
        return chatRoomService.listRooms(requireLoggedIn(authentication));
    }

    @PostMapping("/direct")
    public Map<String, Object> openDirect(@RequestBody ChatDirectRequest request, Authentication authentication) {
        ChatRoom room = chatRoomService.openDirect(requireLoggedIn(authentication), request.nickname());
        return Map.of("id", room.getId());
    }

    @PostMapping("/self")
    public Map<String, Object> openSelf(Authentication authentication) {
        ChatRoom room = chatRoomService.openSelf(requireLoggedIn(authentication));
        return Map.of("id", room.getId());
    }

    @PostMapping("/group")
    public Map<String, Object> createGroup(@RequestBody ChatGroupCreateRequest request, Authentication authentication) {
        ChatRoom room = chatRoomService.createGroup(requireLoggedIn(authentication), request.name(), request.memberNicknames());
        return Map.of("id", room.getId());
    }

    @GetMapping("/{id}")
    public ChatRoomDetailResponse detail(@PathVariable Long id, Authentication authentication) {
        return chatRoomService.detail(id, requireLoggedIn(authentication));
    }

    @PostMapping("/{id}/invite")
    public void invite(@PathVariable Long id, @RequestBody ChatInviteRequest request, Authentication authentication) {
        chatRoomService.invite(id, requireLoggedIn(authentication), request.nickname());
    }

    @DeleteMapping("/{id}/leave")
    public void leave(@PathVariable Long id, Authentication authentication) {
        chatRoomService.leave(id, requireLoggedIn(authentication));
    }

    @GetMapping("/{id}/messages")
    public List<ChatMessageResponse> messages(@PathVariable Long id,
            @RequestParam(required = false) Long beforeId, Authentication authentication) {
        return chatMessageService.history(id, requireLoggedIn(authentication), beforeId);
    }

    @PostMapping("/{id}/messages/file")
    public ChatMessageResponse sendFile(@PathVariable Long id, @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        try {
            return chatMessageService.sendFile(id, requireLoggedIn(authentication), file);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id, Authentication authentication) {
        chatMessageService.markRead(id, requireLoggedIn(authentication));
    }

    @PutMapping("/{id}/messages/{messageId}")
    public ChatMessageResponse editMessage(@PathVariable Long id, @PathVariable Long messageId,
            @RequestBody ChatEditMessageRequest request, Authentication authentication) {
        return chatMessageService.editText(id, messageId, requireLoggedIn(authentication), request.content());
    }

    @DeleteMapping("/{id}/messages/{messageId}")
    public ChatMessageResponse deleteMessage(@PathVariable Long id, @PathVariable Long messageId,
            Authentication authentication) {
        return chatMessageService.deleteMessage(id, messageId, requireLoggedIn(authentication));
    }

    private User requireLoggedIn(Authentication authentication) {
        return currentUserResolver.resolve(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }
}
