package kr.co.dh.globelog.security;

import kr.co.dh.globelog.domain.AdminAccount;
import kr.co.dh.globelog.domain.AdminAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 최초 기동 시 admin_account 테이블이 비어 있으면 부트스트랩 계정 1개를 생성한다.
 * 이후 계정 관리는 어드민 화면에서 수행한다.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapUsername;
    private final String bootstrapPassword;

    public AdminAccountInitializer(
            AdminAccountRepository adminAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.bootstrap-username}") String bootstrapUsername,
            @Value("${app.admin.bootstrap-password}") String bootstrapPassword) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminAccountRepository.count() > 0) {
            return;
        }

        adminAccountRepository.save(new AdminAccount(bootstrapUsername, passwordEncoder.encode(bootstrapPassword)));
        log.info("부트스트랩 관리자 계정을 생성했습니다: {}", bootstrapUsername);
    }
}
