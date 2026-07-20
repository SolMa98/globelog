package kr.co.dh.globelog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 대화방 참여자. lastReadAt으로 안읽음 개수를 계산한다(메시지별로 읽음 여부를 따로
 * 저장하지 않고, "이 시각 이후 온 메시지 = 안읽음"으로 단순화 — 메신저 수준의 정밀한
 * 읽음영수증까지는 필요 없는 개인 프로젝트 규모라 이 절충이 충분하다고 판단).
 */
@Entity
@Table(name = "chat_room_member", uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"}))
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    // null이면 "한 번도 안 읽음"(모든 메시지가 안읽음) 의미.
    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    protected ChatRoomMember() {
    }

    public ChatRoomMember(ChatRoom room, User user) {
        this.room = room;
        this.user = user;
    }

    public Long getId() { return id; }
    public ChatRoom getRoom() { return room; }
    public User getUser() { return user; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public LocalDateTime getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(LocalDateTime lastReadAt) { this.lastReadAt = lastReadAt; }
}
