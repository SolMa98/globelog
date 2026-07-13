package kr.co.dh.globelog.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * IdentityVerificationService가 RestClient.Builder를 생성자로 주입받아(테스트에서 mock으로
 * 교체 가능하도록) 쓰므로, 이 프로젝트에는 없는 RestClient 자동 구성 대신 빈으로 직접 등록한다.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}