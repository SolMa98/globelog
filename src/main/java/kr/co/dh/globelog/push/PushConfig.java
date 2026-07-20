package kr.co.dh.globelog.push;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VapidProperties.class)
public class PushConfig {
}
