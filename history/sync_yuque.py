from __future__ import annotations

import json
import re
import time
from pathlib import Path
from urllib import error, request


REPO_ROOT = Path(__file__).resolve().parents[1]
TOKEN = REPO_ROOT.joinpath("yuque-token.txt").read_text(encoding="utf-8").strip()
BASE = "https://www.yuque.com/api/v2/repos/snoopy-rfzyo/mp8bfs"
HEADERS = {
    "x-auth-token": TOKEN,
    "Content-Type": "application/json",
    "Accept": "application/json",
    "User-Agent": "Codex-Yuque-Sync/1.0",
}


def read_body(relative_path: str) -> str:
    return REPO_ROOT.joinpath(relative_path).read_text(encoding="utf-8")


def shift_headings(text: str) -> str:
    def repl(match: re.Match[str]) -> str:
        return "#" + match.group(1) + match.group(2)

    return re.sub(r"^(#{1,6})(\s+.*)$", repl, text, flags=re.M).strip()


def compose_lua_body() -> str:
    parts = [
        "# 语言基础",
        "",
        "Lua 相关内容已按主题归并到一篇里。",
        "",
        shift_headings(read_body("yuque/语言基础/Lua整理/基础.md")),
        "",
        shift_headings(read_body("yuque/语言基础/Lua整理/函数.md")),
        "",
        shift_headings(read_body("yuque/语言基础/Lua整理/Table.md")),
    ]
    return "\n".join(parts).strip()


def compose_server_body() -> str:
    parts = [
        "# 服务器",
        "",
        "Docker 和 Ubuntu 相关内容已按大类归并。",
        "",
        "## Docker",
        "",
        shift_headings(read_body("yuque/服务器/Docker/安装与配置.md")),
        "",
        shift_headings(read_body("yuque/服务器/Docker/镜像源与排障.md")),
        "",
        shift_headings(read_body("yuque/服务器/Docker/卸载.md")),
        "",
        shift_headings(read_body("yuque/服务器/Docker/容器部署.md")),
        "",
        "## Ubuntu",
        "",
        shift_headings(read_body("yuque/服务器/Ubuntu/Nginx.md")),
        "",
        shift_headings(read_body("yuque/服务器/Ubuntu/Redis.md")),
    ]
    return "\n".join(parts).strip()


def compose_software_body() -> str:
    parts = [
        "# 软件安装及应用",
        "",
        "常用软件、系统环境和开发环境都已按大类归并在这一页。",
        "",
        "## 常用软件",
        "",
        shift_headings(read_body("yuque/软件安装及应用/常用软件.md")),
        "",
        "## 常用链接",
        "",
        shift_headings(read_body("yuque/软件安装及应用/常用链接.md")),
        "",
        "## Windows",
        "",
        shift_headings(read_body("yuque/软件安装及应用/Windows.md")),
        "",
        "## macOS",
        "",
        shift_headings(read_body("yuque/软件安装及应用/macOS.md")),
        "",
        "## Linux",
        "",
        shift_headings(read_body("yuque/软件安装及应用/Linux.md")),
        "",
        "## 开发环境",
        "",
        shift_headings(read_body("yuque/软件安装及应用/开发环境/JDK.md")),
        "",
        shift_headings(read_body("yuque/软件安装及应用/开发环境/Maven.md")),
        "",
        shift_headings(read_body("yuque/软件安装及应用/开发环境/MySQL.md")),
        "",
        shift_headings(read_body("yuque/软件安装及应用/开发环境/Git.md")),
        "",
        shift_headings(read_body("yuque/软件安装及应用/开发环境/Node.js.md")),
    ]
    return "\n".join(parts).strip()


def api_request(method: str, path: str, payload: dict | None = None, attempts: int = 8) -> dict:
    url = f"{BASE}{path}"
    data = None
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")

    delay = 30
    for attempt in range(1, attempts + 1):
        try:
            req = request.Request(url, data=data, headers=HEADERS, method=method)
            with request.urlopen(req, timeout=60) as resp:
                raw = resp.read()
                if not raw:
                    return {}
                return json.loads(raw.decode("utf-8"))
        except error.HTTPError as exc:
            body = exc.read().decode("utf-8", "replace")
            if exc.code == 429 and attempt < attempts:
                print(f"[retry] {method} {path} hit 429, sleep {delay}s")
                time.sleep(delay)
                delay = min(delay * 2, 180)
                continue
            raise RuntimeError(f"{method} {path} failed: {exc.code} {body}") from exc
        except error.URLError as exc:
            if attempt < attempts:
                print(f"[retry] {method} {path} failed ({exc.reason}), sleep {delay}s")
                time.sleep(delay)
                delay = min(delay * 2, 180)
                continue
            raise RuntimeError(f"{method} {path} failed after retries: {exc}") from exc

    raise RuntimeError(f"{method} {path} exhausted retries")


def get_docs() -> list[dict]:
    payload = api_request("GET", "/docs")
    data = payload.get("data", [])
    return data if isinstance(data, list) else []


def find_doc_by_title(title: str) -> dict | None:
    for doc in get_docs():
        if doc.get("title") == title:
            return doc
    return None


def update_doc(slug: str, title: str | None = None, body: str | None = None) -> dict:
    payload: dict = {}
    if title is not None:
        payload["title"] = title
    if body is not None:
        payload["body"] = body
        payload["format"] = "markdown"
    return api_request("PUT", f"/docs/{slug}", payload)


def create_doc(title: str, body: str) -> dict:
    payload = {
        "title": title,
        "body": body,
        "format": "markdown",
        "public": 0,
    }
    return api_request("POST", "/docs", payload)


def main() -> None:
    print("Cooling down before first Yuque write...")
    time.sleep(90)

    lua_body = compose_lua_body()
    server_body = compose_server_body()
    software_body = compose_software_body()

    print("Updating Lua page...")
    update_doc("bgdlrbw1to1d9g44", title="语言基础", body=lua_body)

    time.sleep(10)

    print("Updating software page...")
    update_doc("gbw2kgoc8fuhibk9", title="软件安装及应用", body=software_body)

    time.sleep(10)

    print("Preparing server page...")
    existing_server = find_doc_by_title("服务器")
    if existing_server:
        server_slug = existing_server["slug"]
        print(f"Updating existing server page: {server_slug}")
        update_doc(server_slug, title="服务器", body=server_body)
    else:
        print("Creating server page...")
        created = create_doc("服务器", server_body)
        data = created.get("data", {})
        print(f"Created server page: {data.get('title')} / {data.get('slug')}")

    print("Done.")


if __name__ == "__main__":
    main()
