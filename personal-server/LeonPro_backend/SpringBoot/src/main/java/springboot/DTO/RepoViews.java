package springboot.DTO;

import java.util.List;

/** 手机端 GitHub / Gitee 仓库。访问令牌不出接口。 */
public final class RepoViews {

    private RepoViews() {
    }

    public record Host(boolean configured, String hint, String login, String warning) {
    }

    public record CommitBrief(String sha, String shortSha, String message, String author, String date, String htmlUrl) {
    }

    public record RepoCard(
            String provider,
            String owner,
            String name,
            String fullName,
            boolean privateRepo,
            boolean archived,
            String description,
            String defaultBranch,
            String language,
            String htmlUrl,
            String pushedAt,
            int stars,
            int openIssues,
            CommitBrief lastCommit
    ) {
    }

    public record Board(Host github, Host gitee, List<RepoCard> repos) {
    }

    public record Branch(String name, String sha, String shortSha, String message, String author, String date) {
    }

    public record BranchPage(String defaultBranch, boolean truncated, List<Branch> branches) {
    }

    public record CommitPage(String ref, int page, List<CommitBrief> commits) {
    }

    public record FileChange(String filename, String status, int additions, int deletions, String patch) {
    }

    public record CommitDetail(
            String sha,
            String shortSha,
            String message,
            String author,
            String date,
            String htmlUrl,
            int additions,
            int deletions,
            boolean filesTruncated,
            List<FileChange> files
    ) {
    }
}
