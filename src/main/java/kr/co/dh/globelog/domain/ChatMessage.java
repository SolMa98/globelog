package kr.co.dh.globelog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * type=FILE인데 filePath가 null이면 "첨부파일이 3개월 보관기간이 지나 삭제됨"을 의미한다
 * (ChatAttachmentCleanupService가 파일만 지우고 메시지 자체는 대화 맥락 유지를 위해 남겨둠).
 * deleted=true(작성자 본인이 삭제)도 같은 이유로 행 자체는 지우지 않고 content/파일만
 * 비운다 — 실제 delete 대신 상태값만 바꾸는 방식(soft delete).
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private ChatMessageType type;

    @Lob
    @Column(name = "content")
    private String content;

    @Column(name = "file_path", length = 255)
    private String filePath;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    // null이면 한 번도 수정 안 함 — 삭제된 메시지는 편집 불가라 이 값도 그대로 굳는다.
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ChatMessage() {
    }

    public static ChatMessage text(ChatRoom room, User sender, String content) {
        ChatMessage message = new ChatMessage();
        message.room = room;
        message.sender = sender;
        message.type = ChatMessageType.TEXT;
        message.content = content;
        return message;
    }

    public static ChatMessage file(ChatRoom room, User sender, String filePath, String originalFilename, long fileSize) {
        ChatMessage message = new ChatMessage();
        message.room = room;
        message.sender = sender;
        message.type = ChatMessageType.FILE;
        message.filePath = filePath;
        message.originalFilename = originalFilename;
        message.fileSize = fileSize;
        return message;
    }

    public Long getId() { return id; }
    public ChatRoom getRoom() { return room; }
    public User getSender() { return sender; }
    public ChatMessageType getType() { return type; }
    public String getContent() { return content; }
    public String getFilePath() { return filePath; }
    public String getOriginalFilename() { return originalFilename; }
    public Long getFileSize() { return fileSize; }
    public boolean isDeleted() { return deleted; }
    public LocalDateTime getEditedAt() { return editedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // 3개월 만료 정리 시 파일만 지우고 메시지는 남긴다.
    public void expireFile() {
        this.filePath = null;
    }

    public void editText(String newContent) {
        this.content = newContent;
        this.editedAt = LocalDateTime.now();
    }

    // 실제 row 삭제 대신 상태값만 바꾼다 — content/파일 참조는 비워서 대화 맥락(누가 언제
    // 삭제했는지)만 남기고 실제 내용은 사라지게 한다.
    public void markDeleted() {
        this.deleted = true;
        this.content = null;
        this.filePath = null;
        this.originalFilename = null;
        this.fileSize = null;
    }
}
