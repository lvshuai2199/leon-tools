package springboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springboot.service.repo.GitHostClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RepoConfig {

    @Bean
    public GitHostClient gitHostClient(JsonMapper jsonMapper) {
        return new GitHostClient(GitHostClient.defaultHttp(), jsonMapper);
    }
}
