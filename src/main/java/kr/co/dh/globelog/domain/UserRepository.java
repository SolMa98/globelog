package kr.co.dh.globelog.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByNickname(String nickname);

    Optional<User> findByVerificationToken(String verificationToken);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByIdentityDiHash(String identityDiHash);

    Page<User> findByNicknameContainingIgnoreCase(String nickname, Pageable pageable);
}
