package kr.co.dh.globelog.chat;

import java.time.LocalDateTime;
import java.util.List;
import kr.co.dh.globelog.domain.ChatMessage;
import kr.co.dh.globelog.domain.ChatMessageRepository;
import kr.co.dh.globelog.domain.ChatMessageType;
import kr.co.dh.globelog.domain.ChatRoom;
import kr.co.dh.globelog.domain.ChatRoomMember;
import kr.co.dh.globelog.domain.ChatRoomMemberRepository;
import kr.co.dh.globelog.domain.ChatRoomType;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.file.FileStorageService;
import kr.co.dh.globelog.push.WebPushService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatMessageService {

    private static final int PAGE_SIZE = 30;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int PUSH_BODY_MAX_LENGTH = 80;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatRoomService chatRoomService;
    private final FileStorageService fileStorageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatPresenceService chatPresenceService;
    private final WebPushService webPushService;

    public ChatMessageService(ChatMessageRepository chatMessageRepository,
            ChatRoomMemberRepository chatRoomMemberRepository, ChatRoomService chatRoomService,
            FileStorageService fileStorageService, SimpMessagingTemplate messagingTemplate,
            ChatPresenceService chatPresenceService, WebPushService webPushService) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.chatRoomService = chatRoomService;
        this.fileStorageService = fileStorageService;
        this.messagingTemplate = messagingTemplate;
        this.chatPresenceService = chatPresenceService;
        this.webPushService = webPushService;
    }

    @Transactional
    public ChatMessageResponse sendText(Long roomId, User sender, String content) {
        ChatRoom room = chatRoomService.requireMemberRoom(roomId, sender.getId());
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지 내용을 입력해주세요.");
        }
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지는 " + MAX_CONTENT_LENGTH + "자 이내로 작성해주세요.");
        }
        ChatMessage saved = chatMessageRepository.save(ChatMessage.text(room, sender, trimmed));
        ChatMessageResponse response = ChatMessageResponse.from(saved, sender.getId());
        broadcast(roomId, ChatMessageEvent.NEW, response);
        notifyOtherMembers(room, sender, truncate(trimmed));
        return response;
    }

    @Transactional
    public ChatMessageResponse sendFile(Long roomId, User sender, MultipartFile file) {
        ChatRoom room = chatRoomService.requireMemberRoom(roomId, sender.getId());
        String storedPath = fileStorageService.storeChatAttachment(file);
        ChatMessage saved = chatMessageRepository.save(
                ChatMessage.file(room, sender, storedPath, file.getOriginalFilename(), file.getSize()));
        ChatMessageResponse response = ChatMessageResponse.from(saved, sender.getId());
        broadcast(roomId, ChatMessageEvent.NEW, response);
        notifyOtherMembers(room, sender, "📎 " + file.getOriginalFilename());
        return response;
    }

    // 본인 텍스트 메시지만, 삭제되지 않은 상태에서만 수정할 수 있다. 파일 메시지는
    // 캡션 개념이 없어 수정 대상에서 뺀다.
    @Transactional
    public ChatMessageResponse editText(Long roomId, Long messageId, User editor, String newContent) {
        chatRoomService.requireMemberRoom(roomId, editor.getId());
        ChatMessage message = findOwnMessageOrThrow(roomId, messageId, editor);
        if (message.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제된 메시지는 수정할 수 없습니다.");
        }
        if (message.getType() != ChatMessageType.TEXT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "텍스트 메시지만 수정할 수 있습니다.");
        }
        String trimmed = newContent == null ? "" : newContent.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지 내용을 입력해주세요.");
        }
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지는 " + MAX_CONTENT_LENGTH + "자 이내로 작성해주세요.");
        }
        message.editText(trimmed);
        ChatMessageResponse response = ChatMessageResponse.from(message, editor.getId());
        broadcast(roomId, ChatMessageEvent.EDIT, response);
        return response;
    }

    // 실제 row는 지우지 않고 상태값(deleted)만 바꾼다 — ChatMessage.markDeleted() 참고.
    // 첨부파일이었다면 스토리지 파일도 이 시점에 바로 지운다(더 이상 볼 수 없는 메시지라
    // 3개월 보관정책까지 기다릴 이유가 없음).
    @Transactional
    public ChatMessageResponse deleteMessage(Long roomId, Long messageId, User deleter) {
        chatRoomService.requireMemberRoom(roomId, deleter.getId());
        ChatMessage message = findOwnMessageOrThrow(roomId, messageId, deleter);
        if (message.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 삭제된 메시지입니다.");
        }
        if (message.getType() == ChatMessageType.FILE && message.getFilePath() != null) {
            fileStorageService.delete(message.getFilePath());
        }
        message.markDeleted();
        ChatMessageResponse response = ChatMessageResponse.from(message, deleter.getId());
        broadcast(roomId, ChatMessageEvent.DELETE, response);
        return response;
    }

    private ChatMessage findOwnMessageOrThrow(Long roomId, Long messageId, User user) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다: " + messageId));
        if (!message.getRoom().getId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다: " + messageId);
        }
        if (!message.getSender().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 작성한 메시지만 가능합니다.");
        }
        return message;
    }

    // 최신 메시지부터: beforeId가 없으면 맨 처음 진입(최신 PAGE_SIZE개), 있으면 그보다
    // 오래된 메시지를 더 불러오는 무한스크롤 커서. 응답은 항상 최신순이라 클라이언트가
    // 화면에 그릴 때 뒤집어서 오래된 것부터 보여준다.
    public List<ChatMessageResponse> history(Long roomId, User viewer, Long beforeId) {
        chatRoomService.requireMemberRoom(roomId, viewer.getId());
        Pageable page = PageRequest.of(0, PAGE_SIZE);
        List<ChatMessage> messages = beforeId != null
                ? chatMessageRepository.findByRoomIdAndIdLessThanOrderByCreatedAtDesc(roomId, beforeId, page)
                : chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, page);
        return messages.stream().map(m -> ChatMessageResponse.from(m, viewer.getId())).toList();
    }

    @Transactional
    public void markRead(Long roomId, User viewer) {
        chatRoomService.requireMemberRoom(roomId, viewer.getId());
        ChatRoomMember member = chatRoomMemberRepository.findByRoomIdAndUserId(roomId, viewer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "참여 중인 대화방이 아닙니다."));
        member.setLastReadAt(LocalDateTime.now());
    }

    private void broadcast(Long roomId, String type, ChatMessageResponse response) {
        messagingTemplate.convertAndSend("/topic/chat." + roomId, ChatMessageEvent.of(type, response));
    }

    // 보낸 사람 본인과, 지금 그 방 화면을 실제로 보고 있는 사람(ChatPresenceService)은
    // 제외하고 나머지 멤버에게만 브라우저 푸시를 보낸다 — 이미 실시간으로 화면에 뜨는데
    // 알림까지 중복으로 뜨면 성가시기 때문.
    private void notifyOtherMembers(ChatRoom room, User sender, String body) {
        String title = pushTitle(room, sender);
        String url = "/my/chat/" + room.getId();
        chatRoomMemberRepository.findByRoomId(room.getId()).stream()
                .map(ChatRoomMember::getUser)
                .filter(member -> !member.getId().equals(sender.getId()))
                .filter(member -> !chatPresenceService.isViewing(room.getId(), member.getId()))
                .forEach(member -> webPushService.notify(member, title, body, url));
    }

    private String pushTitle(ChatRoom room, User sender) {
        if (room.getType() == ChatRoomType.GROUP) {
            return room.getName() + " · " + sender.getNickname();
        }
        return sender.getNickname();
    }

    private String truncate(String content) {
        return content.length() > PUSH_BODY_MAX_LENGTH ? content.substring(0, PUSH_BODY_MAX_LENGTH) + "…" : content;
    }
}
