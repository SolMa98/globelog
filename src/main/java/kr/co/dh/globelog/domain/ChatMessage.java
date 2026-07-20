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
    public LocalDateTime getCreatedAt() { return createdAt; }

    // 3개월 만료 정리 시 파일만 지우고 메시지는 남긴다.
    public void expireFile() {
        this.filePath = null;
    }
}
