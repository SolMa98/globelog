package kr.co.dh.globelog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// EnableScheduling: 채팅 첨부파일 3개월 만료 정리(ChatAttachmentCleanupService)가 @Scheduled를 씀
// EnableAsync: 보안 감사 로그 적재(SecurityEventLogListener)가 @Async로 요청 스레드를 안 막게 함
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class GlobelogApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlobelogApplication.class, args);
    }

}
