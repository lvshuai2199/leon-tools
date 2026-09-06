const USER_KEY = "userInfo";
const USER_ID_KEY = "userId";
const LOGOUT_FLAG = "justLoggedOut";

function read(key) {
  try {
    const raw = localStorage.getItem(key);
    if (raw == null || raw === "") return null;
    try {
      return JSON.parse(raw);
    } catch {
      return raw;
    }
  } catch {
    return null;
  }
}

function write(key, value) {
  localStorage.setItem(key, typeof value === "string" ? value : JSON.stringify(value));
}

function remove(key) {
  localStorage.removeItem(key);
}

export function getUserInfo() {
  try {
    let user = read(USER_KEY);
    if (!user || typeof user !== "object") return null;
    if (!user.id && user.userId) user.id = user.userId;
    return user.id ? user : null;
  } catch {
    return null;
  }
}

export function setUserInfo(user) {
  remove(LOGOUT_FLAG);
  write(USER_KEY, user || {});
  if (user?.id) write(USER_ID_KEY, user.id);
}

export function clearUserInfo() {
  remove(USER_KEY);
  remove(USER_ID_KEY);
  write(LOGOUT_FLAG, "1");
}

export function consumeLogoutFlag() {
  try {
    const flag = read(LOGOUT_FLAG);
    if (flag) {
      remove(LOGOUT_FLAG);
      return true;
    }
  } catch {
    return false;
  }
  return false;
}

export function isLoggedIn() {
  return !!getUserInfo();
}

export function canEnterApp(user) {
  if (!user) return false;
  const roleId = String(user.roleId || "");
  const roleName = String(user.roleName || "").toUpperCase();
  if (roleId === "role_root" || roleName === "ROOT") return true;
  if (roleId === "role_regcode_client") return true;
  return !!(user.parentId && String(user.parentId).trim());
}
