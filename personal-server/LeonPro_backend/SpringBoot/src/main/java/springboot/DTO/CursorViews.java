package springboot.DTO;

import java.util.List;

/** 手机端 Cursor 任务。agent id 和 API Key 都不出接口。 */
public final class CursorViews {

    private CursorViews() {
    }

    public record TaskCard(
            String id,
            String name,
            String agentStatus,
            String runStatus,
            String repoUrl,
            String updatedAt
    ) {
    }

    public record TaskDetail(
            String id,
            String name,
            String agentStatus,
            String runStatus,
            String promptPreview,
            String repoUrl,
            String startingRef,
            String resultText,
            String agentUrl,
            String prUrl,
            String branchName,
            String updatedAt
    ) {
    }

    public record Board(boolean configured, String hint, String warning, List<TaskCard> tasks) {
    }

    public record Repo(String owner, String name, String url) {
    }

    public record RepoList(List<Repo> repos, String warning) {
    }

    /** Cursor Models 额度。remainingPercent 是剩余百分比；读不到时 available 为 false。 */
    public record Quota(boolean available, boolean unlimited, Integer remainingPercent, String resetAt, String warning) {
    }
}
