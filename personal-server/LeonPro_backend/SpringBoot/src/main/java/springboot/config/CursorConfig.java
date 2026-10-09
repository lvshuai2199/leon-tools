package springboot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springboot.service.cursor.CursorCloudClient;
import springboot.service.cursor.CursorKeyCipher;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class CursorConfig {

    @Bean
    public CursorKeyCipher cursorKeyCipher(@Value("${cursor.key-secret:}") String secret) {
        return new CursorKeyCipher(secret);
    }

    @Bean
    public CursorCloudClient cursorCloudClient(
            @Value("${cursor.api-base:https://api.cursor.com}") String apiBase,
            JsonMapper jsonMapper) {
        return new CursorCloudClient(CursorCloudClient.defaultHttp(), apiBase, jsonMapper);
    }
}
