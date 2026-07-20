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
 * 브라우저 Web Push 구독 정보(endpoint + 암호화 키). 사용자 하나가 기기/브라우저별로
 * 여러 개를 가질 수 있다 — endpoint 자체가 브라우저가 발급하는 유일값이라 이걸로 구분한다.
 */
@Entity
@Table(name = "push_subscription", uniqueConstraints = @UniqueConstraint(columnNames = "endpoint"))
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 푸시 서비스가 발급하는 구독 URL. FCM/Mozilla 전부 500자를 넘지 않아 인덱싱 가능한
    // VARCHAR로 둔다(Lob/TEXT로 두면 조회·유니크 제약이 애매해짐).
    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    @Column(name = "p256dh", nullable = false, length = 255)
    private String p256dh;

    @Column(name = "auth", nullable = false, length = 255)
    private String auth;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PushSubscription() {
    }

    public PushSubscription(User user, String endpoint, String p256dh, String auth) {
        this.user = user;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
