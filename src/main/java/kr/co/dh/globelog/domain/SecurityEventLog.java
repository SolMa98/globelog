package kr.co.dh.globelog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 관리자 백오피스 "보안 로그" 화면에 노출되는 감사 로그 한 건. 로그인/로그아웃(관리자·일반
 * 사용자 구분), 게시글(Trip) 등록/수정/삭제/조회, 채팅 이벤트를 기록한다.
 *
 * actor는 User/AdminAccount에 대한 FK가 아니라 id+라벨 스냅샷으로 남긴다 — 계정이
 * 삭제되거나(회원 탈퇴 등) 닉네임이 바뀌어도 그 시점의 로그 내용이 그대로 보존되어야
 * 감사 기록으로서 의미가 있기 때문. actorLabel은 User는 nickname, AdminAccount는
 * username을 담는다(User.email은 평문 저장 이슈가 있어 로그에는 남기지 않음 — User.java
 * 상단 TODO 참고).
 *
 * 쓰기는 SecurityAuditService → ApplicationEventPublisher → 비동기 리스너 경로로만 이뤄진다
 * (실시간 요청 경로를 감사 로그 INSERT가 지연시키지 않도록, 그리고 로그 기록 실패가 본
 * 기능에 영향을 주지 않도록 하기 위함). 엔티티를 직접 new해서 저장하지 말 것.
 */
@Entity
@Table(name = "security_event_log", indexes = {
        @Index(name = "idx_sec_log_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_sec_log_event_type", columnList = "event_type"),
        @Index(name = "idx_sec_log_actor", columnList = "actor_type,actor_id")
})
public class SecurityEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private SecurityEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private SecurityActorType actorType;

    // 로그인 실패 등 계정을 특정할 수 없는 이벤트에서는 null (actorLabel에 시도된 값만 남김)
    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_label", length = 100)
    private String actorLabel;

    // 예: "TRIP", "CHAT_ROOM", "CHAT_MESSAGE" — 대상이 없는 이벤트(로그인 등)는 null
    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "detail", length = 200)
    private String detail;

    // IPv6 표기(::ffff:0:0/96 등 확장 포함) 대비 45자
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    protected SecurityEventLog() {
    }

    public SecurityEventLog(LocalDateTime occurredAt, SecurityEventType eventType, SecurityActorType actorType,
            Long actorId, String actorLabel, String targetType, Long targetId, String detail,
            String ipAddress, String userAgent) {
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.actorType = actorType;
        this.actorId = actorId;
        this.actorLabel = actorLabel;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public SecurityEventType getEventType() {
        return eventType;
    }

    public SecurityActorType getActorType() {
        return actorType;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
