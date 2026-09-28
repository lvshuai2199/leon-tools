// 访问 token 缓存的 key（后端 /auth/login 返回的 token，请求时以 Authorization: Bearer <token> 携带）
const ACCESS_TOKEN_KEY = "access_token";
// 旧版本遗留的刷新 token key（后端没有刷新接口），仅用于清理
const LEGACY_REFRESH_TOKEN_KEY = "refresh_token";
// 旧版本登录写入的假会话标记前缀，不是后端签发的 token
const LEGACY_SESSION_PREFIX = "session-";

function getAccessToken(): string {
  return localStorage.getItem(ACCESS_TOKEN_KEY) || "";
}

function setAccessToken(token: string) {
  localStorage.setItem(ACCESS_TOKEN_KEY, token);
}

function clearToken() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(LEGACY_REFRESH_TOKEN_KEY);
}

/** 旧版本的假 token（session-时间戳），需要强制重新登录 */
function isLegacySessionToken(token: string) {
  return token.startsWith(LEGACY_SESSION_PREFIX);
}

export { getAccessToken, setAccessToken, clearToken, isLegacySessionToken };
