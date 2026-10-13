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

    /** stale 为 true 时这是超过半天的缓存，页面可以先显示，再拉新的替换。 */
    public record Board(Host github, Host gitee, List<RepoCard> repos, boolean stale) {
    }

    /**
     * merge：相对默认分支。default 默认分支，identical 同一提交，merged 已合并（只落后），
     * ahead 未合并且可快进，diverged 两边都有新提交，unmerged 有未合并提交但不知道落后多少，空字符串表示没比出来。
     * ahead 是这条分支多出来的提交，behind 是默认分支多出来的提交。
     */
    public record Branch(String name, String sha, String shortSha, String message, String author, String date,
                         String merge, int ahead, int behind) {
    }

    public record BranchPage(String defaultBranch, boolean truncated, List<Branch> branches) {
    }

    public record CommitPage(String ref, int page, List<CommitBrief> commits, boolean stale) {
    }

    /** parents 是父提交。branches 是顶端正好落在这次提交上的分支名。 */
    public record GraphCommit(String sha, String shortSha, String message, String author, String date,
                              List<String> parents, List<String> branches) {
    }

    public record GraphPage(String defaultBranch, boolean truncated, List<Branch> branches, List<GraphCommit> commits, boolean stale) {
    }

    /** outcome：merged 刚合并，already 本来就在目标分支里。 */
    public record MergeResult(String outcome, String sha, String message) {
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
