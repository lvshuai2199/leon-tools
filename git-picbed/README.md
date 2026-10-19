# Git 图床

本地网页工具。托管在 **Gitee** 或 **GitHub** 里选一个，图片和文档都用 git 提交、推送。

国内网络建议用 **Gitee**（本工具默认也是 Gitee）。`origin` 必须和所选托管一致，否则会推错远程、文档里写出另一家的 raw 链接。

假设：图床仓库已经 clone 到本机，并且这个仓库的 `user.name` / `user.email` 已配置，本机可以 `git push`。仓库需要**公开**，raw 链接才能被文档打开。勾选自动推送后，文件变化会自己 commit 和 push。

## Gitee 用法清单

1. Gitee 上新建**公开**仓库，本机 `git clone` 下来，`git remote -v` 确认 `origin` 是 `gitee.com/...`（不能是 github.com）。
2. 图床页面选 **Gitee**。若提示「当前 origin 实际是 GitHub」，先改远程：
   ```bat
   git remote set-url origin https://gitee.com/<owner>/<repo>.git
   ```
3. **图片分支**填的要和 Gitee 默认分支一致（常见 `master` 或 `main`），或单独建图片分支后确保已 push 过该分支。
4. 监听文件夹建议：`D:\\GitFiles\\tempFiles\\imgs`；文档文件夹指向笔记仓（如 leon-notes）里写 md 的目录。
5. 先点 **整理**（只本地 commit），再点 **推送**。只有推送成功后，文档里的本地图片路径才会改成 `raw.giteeusercontent.com` 外链；未推送会保留本地路径。
6. 刚推上去时 CDN 可能缓存 **1–5 分钟**，浏览器直接打开 raw 仍 404 时可稍后再试。
7. Typora 打不开图时：用浏览器打开表格里的「打开」自检；确认是 `raw.giteeusercontent.com/.../raw/<分支>/...`，不要用会返回 HTML 跳转的 `gitee.com/.../raw/...`。
8. 若网页有防盗链导致图裂，可在需要的页面加：
   ```html
   <meta name="referrer" content="no-referrer">
   ```
   Typora 本地预览一般无妨。

### 已有图在 GitHub、要迁到 Gitee

1. 在 Gitee 建好公开空仓，本地图床仓执行：
   ```bat
   git remote set-url origin https://gitee.com/<owner>/<repo>.git
   git push -u origin --all
   git push origin --tags
   ```
2. 图床页面改选 Gitee，确认「当前 origin」已是 gitee.com，再点一次 **推送**（会按 Gitee raw 改写文档链接）。
3. 不需要双远程；本工具只认配置的 `origin`。若暂时保留 github 作备份，可另加 `git remote add github ...`，但页面仍必须选与 `origin` 一致的那一家。

## 用法

1. 双击 `启动.bat`，打开 `http://127.0.0.1:3791`。
2. 选择 Gitee 或 GitHub。`origin` 必须指向同一家。
3. 填写本地仓库、图片分支、图片目录，以及监听文件夹、文档文件夹。
4. 预览整理，确认新增 / 复用 / 清除。
5. 整理只提交图片。推送会先把图片推上去，再改文档链接并推送。勾选自动推送后，文件变化约 8 秒会自己做完这两步，间隔时间是兜底。
6. 还没推送的图片先留本地路径。源文件不存在时，原位置改成红色的「图片已丢失」。

单张超过 50MB 会跳过。监听文件夹路径不存在时不会清理，避免填错目录把图床删空。

Gitee 图片链接用 `raw.giteeusercontent.com`。`gitee.com/.../raw/...` 会先返回一段 HTML 跳转，Typora 不跟随，图片就显示不出来。

配置写在本目录 `config.json`（已忽略，不提交）。示例见 `config.example.json`。
