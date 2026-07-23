package kr.co.dh.globelog.chat;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import kr.co.dh.globelog.domain.ChatMessage;
import kr.co.dh.globelog.domain.ChatMessageRepository;
import kr.co.dh.globelog.domain.ChatMessageType;
import kr.co.dh.globelog.domain.ChatRoom;
import kr.co.dh.globelog.domain.ChatRoomMember;
import kr.co.dh.globelog.domain.ChatRoomMemberRepository;
import kr.co.dh.globelog.domain.ChatRoomRepository;
import kr.co.dh.globelog.domain.ChatRoomType;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventType;
import kr.co.dh.globelog.domain.User;
import kr.co.dh.globelog.domain.UserRepository;
import kr.co.dh.globelog.security.audit.SecurityAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 대화방 생성/조회/멤버 관리. 메시지 자체(전송/이력)는 ChatMessageService가 맡는다.
 */
@Service
public class ChatRoomService {

    private static final int MAX_ROOM_NAME_LENGTH = 100;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final SecurityAuditService securityAuditService;

    public ChatRoomService(ChatRoomRepository chatRoomRepository, ChatRoomMemberRepository chatRoomMemberRepository,
            ChatMessageRepository chatMessageRepository, UserRepository userRepository,
            SecurityAuditService securityAuditService) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.securityAuditService = securityAuditService;
    }

    // 같은 두 사람 사이엔 DIRECT 방이 하나만 있어야 하므로 항상 먼저 찾아보고, 없을 때만 만든다.
    @Transactional
    public ChatRoom openDirect(User me, String targetNickname) {
        User target = findUserOrThrow(targetNickname);
        if (target.getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신과는 1:1 대화를 시작할 수 없습니다. 개인 채팅방을 이용해주세요.");
        }
        return chatRoomRepository.findDirectRoomBetween(me.getId(), target.getId())
                .orElseGet(() -> {
                    ChatRoom room = chatRoomRepository.save(new ChatRoom(ChatRoomType.DIRECT, null));
                    chatRoomMemberRepository.save(new ChatRoomMember(room, me));
                    chatRoomMemberRepository.save(new ChatRoomMember(room, target));
                    securityAuditService.record(SecurityEventType.CHAT_ROOM_CREATE, SecurityActorType.USER,
                            me.getId(), me.getNickname(), "CHAT_ROOM", room.getId(),
                            "1:1 대화방 · 상대: " + target.getNickname());
                    return room;
                });
    }

    @Transactional
    public ChatRoom openSelf(User me) {
        return chatRoomRepository.findSelfRoom(me.getId())
                .orElseGet(() -> {
                    ChatRoom room = chatRoomRepository.save(new ChatRoom(ChatRoomType.SELF, null));
                    chatRoomMemberRepository.save(new ChatRoomMember(room, me));
                    securityAuditService.record(SecurityEventType.CHAT_ROOM_CREATE, SecurityActorType.USER,
                            me.getId(), me.getNickname(), "CHAT_ROOM", room.getId(), "나와의 채팅");
                    return room;
                });
    }

    @Transactional
    public ChatRoom createGroup(User creator, String name, List<String> memberNicknames) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "방 이름을 입력해주세요.");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() > MAX_ROOM_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "방 이름은 " + MAX_ROOM_NAME_LENGTH + "자 이내로 입력해주세요.");
        }
        ChatRoom room = chatRoomRepository.save(new ChatRoom(ChatRoomType.GROUP, trimmedName));
        chatRoomMemberRepository.save(new ChatRoomMember(room, creator));
        for (String nickname : memberNicknames == null ? List.<String>of() : memberNicknames) {
            User user = findUserOrThrow(nickname);
            if (!user.getId().equals(creator.getId())
                    && !chatRoomMemberRepository.existsByRoomIdAndUserId(room.getId(), user.getId())) {
                chatRoomMemberRepository.save(new ChatRoomMember(room, user));
            }
        }
        securityAuditService.record(SecurityEventType.CHAT_ROOM_CREATE, SecurityActorType.USER,
                creator.getId(), creator.getNickname(), "CHAT_ROOM", room.getId(), "그룹방 · " + trimmedName);
        return room;
    }

    // 그룹방은 멤버라면 누구나 다른 사람을 초대할 수 있다(방장 개념 없음).
    @Transactional
    public void invite(Long roomId, User inviter, String targetNickname) {
        ChatRoom room = requireMemberRoom(roomId, inviter.getId());
        if (room.getType() != ChatRoomType.GROUP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "그룹 채팅방에서만 초대할 수 있습니다.");
        }
        User target = findUserOrThrow(targetNickname);
        if (chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 참여 중인 사용자입니다.");
        }
        chatRoomMemberRepository.save(new ChatRoomMember(room, target));
        securityAuditService.record(SecurityEventType.CHAT_INVITE, SecurityActorType.USER,
                inviter.getId(), inviter.getNickname(), "CHAT_ROOM", room.getId(), "초대 대상: " + target.getNickname());
    }

    @Transactional
    public void leave(Long roomId, User user) {
        ChatRoom room = requireMemberRoom(roomId, user.getId());
        if (room.getType() != ChatRoomType.GROUP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "그룹 채팅방만 나갈 수 있습니다.");
        }
        chatRoomMemberRepository.deleteByRoomIdAndUserId(roomId, user.getId());
        securityAuditService.record(SecurityEventType.CHAT_LEAVE, SecurityActorType.USER,
                user.getId(), user.getNickname(), "CHAT_ROOM", room.getId(), null);
    }

    public List<ChatRoomSummaryResponse> listRooms(User viewer) {
        return chatRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(viewer.getId()).stream()
                .map(membership -> buildSummary(membership, viewer))
                .sorted(Comparator.comparing(ChatRoomSummaryResponse::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public ChatRoomDetailResponse detail(Long roomId, User viewer) {
        ChatRoom room = requireMemberRoom(roomId, viewer.getId());
        List<ChatRoomMemberResponse> members = chatRoomMemberRepository.findByRoomId(roomId).stream()
                .map(m -> new ChatRoomMemberResponse(m.getUser().getId(), m.getUser().getNickname(), m.getUser().getProfileImageUrl()))
                .toList();
        String displayName = switch (room.getType()) {
            case SELF -> "나와의 채팅";
            case GROUP -> room.getName();
            case DIRECT -> members.stream()
                    .map(ChatRoomMemberResponse::nickname)
                    .filter(nickname -> !nickname.equals(viewer.getNickname()))
                    .findFirst()
                    .orElse(room.getName());
        };
        return new ChatRoomDetailResponse(room.getId(), room.getType().name(), displayName, members);
    }

    // 소유권 체크: 로그인한 사용자가 실제로 그 방의 멤버인지 항상 확인한 뒤에만 반환한다.
    public ChatRoom requireMemberRoom(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대화방을 찾을 수 없습니다: " + roomId));
        if (!chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "참여 중인 대화방이 아닙니다.");
        }
        return room;
    }

    private ChatRoomSummaryResponse buildSummary(ChatRoomMember membership, User viewer) {
        ChatRoom room = membership.getRoom();
        Optional<ChatMessage> last = chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(room.getId());
        long unread = chatMessageRepository.countByRoomIdAndCreatedAtAfter(room.getId(),
                membership.getLastReadAt() != null ? membership.getLastReadAt() : LocalDateTime.MIN);

        String displayName;
        String displayImageUrl;
        if (room.getType() == ChatRoomType.SELF) {
            displayName = "나와의 채팅";
            displayImageUrl = viewer.getProfileImageUrl();
        } else if (room.getType() == ChatRoomType.GROUP) {
            displayName = room.getName();
            displayImageUrl = null;
        } else {
            User other = otherMember(room.getId(), viewer.getId());
            displayName = other.getNickname();
            displayImageUrl = other.getProfileImageUrl();
        }

        String preview = last.map(m -> m.getType() == ChatMessageType.FILE
                ? "📎 " + (m.getFilePath() != null ? m.getOriginalFilename() : "(만료된 파일)")
                : m.getContent()).orElse(null);

        return new ChatRoomSummaryResponse(room.getId(), room.getType().name(), displayName, displayImageUrl,
                preview, last.map(ChatMessage::getCreatedAt).orElse(null), unread);
    }

    private User otherMember(Long roomId, Long viewerId) {
        return chatRoomMemberRepository.findByRoomId(roomId).stream()
                .map(ChatRoomMember::getUser)
                .filter(u -> !u.getId().equals(viewerId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DIRECT 방에 상대방이 없습니다: " + roomId));
    }

    private User findUserOrThrow(String nickname) {
        return userRepository.findByNickname(nickname)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다: " + nickname));
    }
}
