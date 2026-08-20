# 语雀接口备忘

## 基础信息
- Token 文件：`yuque-token.txt`
- Base URL：`https://www.yuque.com/api/v2`
- 认证头：`x-auth-token: <token>`
- 当前知识库：`snoopy-rfzyo/mp8bfs`

## 常用接口
- 获取目录：`GET /repos/{namespace}/toc`
- 获取文档：`GET /repos/{namespace}/docs/{slug}`
- 更新文档：`PUT /repos/{namespace}/docs/{slug}`
- 更新目录：`PUT /repos/{namespace}/toc`

## 实测可用写法
### 1. 改标题
```json
{"title":"开发环境"}
```

### 2. 移动已有 TOC 节点
```json
{"action":"appendNode","action_mode":"child","target_uuid":"pNTALsG0qoufp-d6","node_uuid":"u1HpwKD8Uu-3mLir"}
```

### 3. 新建目录标题
```json
{"action":"appendNode","action_mode":"child","target_uuid":"","type":"TITLE","title":"新分组"}
```

## 备注
- `node_uuid` 是 TOC 节点 UUID，不是文档 slug。
- `target_uuid` 填父节点 UUID；根节点用空字符串。
- `Invoke-RestMethod` 直接发 JSON body 可用。

## 当前整理结果
- `语言基础`
- `工作记录`
- `开发环境`
  - `开发环境配置`
  - `NVM`
  - `环境索引`
  - `环境管理软件`
    - `环境的安装及处理`
      - `Codex`
      - `Python installer manager`
      - `Claude Code`
- `生活杂记`

## 常用 slug
- `工作记录` -> `ore5dvpdcss2i4q8`
- `开发环境` -> `gbw2kgoc8fuhibk9`
- `环境索引` -> `ory0owlr7qq14z0o`
- `环境管理软件` -> `uxugqxuxnvwpcey2`
- `开发环境配置` -> `duvkt8r9tlgokysm`
- `NVM` -> `tgapilu00zuhxag3`
