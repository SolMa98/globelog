package kr.co.dh.globelog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 관리자 백오피스(/admin/**) 접근을 허용할 IP/CIDR 목록. 단일 IP("203.0.113.5")와
 * CIDR 표기("192.168.1.0/24") 모두 허용 — 매칭은 AdminIpWhitelistFilter에서
 * Spring Security의 IpAddressMatcher로 처리한다.
 */
@Entity
@Table(name = "admin_ip_whitelist")
public class AdminIpWhitelistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cidr", nullable = false, unique = true, length = 100)
    private String cidr;

    @Column(name = "description", length = 200)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AdminIpWhitelistEntry() {
    }

    public AdminIpWhitelistEntry(String cidr, String description) {
        this.cidr = cidr;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getCidr() {
        return cidr;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
