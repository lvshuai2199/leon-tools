---
name: leonpro-regcode-access
description: Diagnose and fix LeonPro 注册码 Web permission errors (无权限管理注册码用户或配置), duplicate usernames, X-User-Id vs getMenuList mismatch, and role_regcode vs 子用户. Use when 注册码用户/配置 APIs deny a user who can see those menus.
---

# LeonPro 注册码权限

## Model

- Web operator: `parent_id` empty. Role `regCode` may manage 注册码用户/配置 if those menus are on the role.
- 子用户: `parent_id` set (role often `role_regcode_client`). Phone H5 generate-only, no Vue Web login.
- Identity is header `X-User-Id` / `X-Username` (no JWT). Do not treat query `username` as the current user.

## Reproduce

1. Confirm Vue `3000` → `/dev-api` → `http://localhost:8089`.
2. MySQL for local Java is usually SSH tunnel `127.0.0.1:13306` (not the public host from this PC).
3. List collisions:

```sql
SELECT id, username, role_id, parent_id
FROM sys_users
WHERE username IN (
  SELECT username FROM sys_users GROUP BY username HAVING COUNT(*) > 1
);
```

4. `POST /auth/login2` then `GET /regCodeUser/getAll` with `X-User-Id` of **each** duplicate row.

## Known failure

Same username `youbo` had one `regCode` operator and extra `role_regcode_client` rows. Sidebar used `getMenuList?username=youbo` (operator menus). APIs used stale `X-User-Id` of a client clone → `无权限管理注册码用户或配置`.

`RegCodeAccessService.currentUser` must prefer the same-username row that `isManager`. `login2` / username lookup must `pickPreferredUser`, not `getOne`.

## After code change

Restart Spring Boot on 8089. Hard refresh Vue. If localStorage still has a clone id, operator APIs should still pass via username twin; otherwise logout and login `youbo` again.
