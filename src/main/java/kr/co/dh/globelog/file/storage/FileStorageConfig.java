package kr.co.dh.globelog.file.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfig {

    @Bean
    public FileStorage fileStorage(FileStorageProperties properties) {
        return switch (properties.mode()) {
            case LOCAL -> new LocalFileStorage(properties.local().baseDir());
            case SCP -> new ScpFileStorage(properties.scp());
        };
    }
}
