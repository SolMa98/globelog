package kr.co.dh.globelog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import kr.co.dh.globelog.security.crypto.EncryptedStringConverter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 셀프가입으로 만드는 일반 사용자 계정(자기 여행 기록의 소유자, 공개 피드에 노출됨).
 * 관리자 백오피스 로그인(AdminAccount)과는 별개의 인증 영역 — 운영자가 자기 여행
 * 기록을 갖고 싶으면 이 User로 별도 가입하는 식으로 분리한다.
 */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인 식별자. TODO: 개인정보 암호화 규칙과 조회용 unique 인덱스가 충돌함 —
    // 애플리케이션 레벨 암호화(랜덤 IV)를 걸면 WHERE email = ? 조회/유니크 제약이 깨지므로,
    // 결정적 암호화나 별도 lookup-hash 컬럼, 혹은 DB 레벨 암호화(TDE) 중 방식을 별도로
    // 정하고 나서 적용할 것. 지금은 평문으로 둠.
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    // 피드/글에 노출되는 공개 표시명(=핸들). 향후 /u/{nickname} 형태 링크에도 쓰일 수 있어 unique.
    @Column(name = "nickname", nullable = false, unique = true, length = 30)
    private String nickname;

    @Column(name = "profile_image_url", length = 255)
    private String profileImageUrl;

    @Column(name = "bio", length = 300)
    private String bio;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    // 이메일/비밀번호 가입 시 본인확인 여부. 소셜 로그인으로 검증된 이메일을 통해
    // 승격되기도 한다(CustomOAuth2UserService 참고). 미인증 상태면 로그인 자체를 막는다.
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "verification_token", length = 100)
    private String verificationToken;

    @Column(name = "verification_token_expires_at")
    private LocalDateTime verificationTokenExpiresAt;

    // 2차 인증(TOTP 앱) 사용 여부와 시크릿. 로그인 방식(비밀번호/소셜)과 무관하게 계정 단위로 적용.
    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "totp_secret", length = 255)
    private String totpSecret;

    // 2차 인증(이메일 코드) 사용 여부.
    @Column(name = "email_otp_enabled", nullable = false)
    private boolean emailOtpEnabled = false;

    // 실명 본인확인(PortOne+다날) 여부 — 계정 생성 전에 검증을 마쳐야 하므로(IdentityVerificationService
    // 참고) 이 앱에서 만들어지는 User는 항상 true로 시작한다. CI/이름/생년월일 등 원본 개인정보는
    // 저장하지 않고, 중복가입 판정에만 쓰는 DI의 되돌릴 수 없는 해시만 남긴다(데이터 최소수집).
    @Column(name = "identity_verified", nullable = false)
    private boolean identityVerified = false;

    @Column(name = "identity_verified_at")
    private LocalDateTime identityVerifiedAt;

    @Column(name = "identity_di_hash", unique = true, length = 64)
    private String identityDiHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected User() {
    }

    public User(String email, String password, String nickname) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public LocalDateTime getVerificationTokenExpiresAt() {
        return verificationTokenExpiresAt;
    }

    public void setVerificationTokenExpiresAt(LocalDateTime verificationTokenExpiresAt) {
        this.verificationTokenExpiresAt = verificationTokenExpiresAt;
    }

    public boolean isTotpEnabled() {
        return totpEnabled;
    }

    public void setTotpEnabled(boolean totpEnabled) {
        this.totpEnabled = totpEnabled;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    public boolean isEmailOtpEnabled() {
        return emailOtpEnabled;
    }

    public void setEmailOtpEnabled(boolean emailOtpEnabled) {
        this.emailOtpEnabled = emailOtpEnabled;
    }

    public boolean isTwoFactorEnabled() {
        return totpEnabled || emailOtpEnabled;
    }

    public boolean isIdentityVerified() {
        return identityVerified;
    }

    public void setIdentityVerified(boolean identityVerified) {
        this.identityVerified = identityVerified;
    }

    public LocalDateTime getIdentityVerifiedAt() {
        return identityVerifiedAt;
    }

    public void setIdentityVerifiedAt(LocalDateTime identityVerifiedAt) {
        this.identityVerifiedAt = identityVerifiedAt;
    }

    public String getIdentityDiHash() {
        return identityDiHash;
    }

    public void setIdentityDiHash(String identityDiHash) {
        this.identityDiHash = identityDiHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
