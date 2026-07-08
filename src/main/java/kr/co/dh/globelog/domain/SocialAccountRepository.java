package kr.co.dh.globelog.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderId(SocialProvider provider, String providerId);

    boolean existsByUserIdAndProvider(Long userId, SocialProvider provider);
}
