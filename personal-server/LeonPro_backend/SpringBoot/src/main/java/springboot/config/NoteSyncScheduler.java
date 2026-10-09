package springboot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import springboot.service.NoteSourceService;

/** 定时看远程分支有没有新提交，有就重新拉取并重建 Markdown 索引。 */
@Slf4j
@Component
public class NoteSyncScheduler {

    private final NoteSourceService noteSourceService;

    public NoteSyncScheduler(NoteSourceService noteSourceService) {
        this.noteSourceService = noteSourceService;
    }

    @Scheduled(fixedDelayString = "${app.notes.poll-millis:60000}",
            initialDelayString = "${app.notes.poll-initial-millis:20000}")
    public void poll() {
        try {
            noteSourceService.poll();
        } catch (Exception e) {
            log.warn("笔记仓库检查失败: {}", e.getMessage());
        }
    }
}
