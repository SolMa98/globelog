package kr.co.dh.globelog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// EnableScheduling: 채팅 첨부파일 3개월 만료 정리(ChatAttachmentCleanupService)가 @Scheduled를 씀
@SpringBootApplication
@EnableScheduling
public class GlobelogApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlobelogApplication.class, args);
    }

}
