const USER_KEY = "userInfo";
const USER_ID_KEY = "userId";
const LOGOUT_FLAG = "justLoggedOut";

export function getUserInfo() {
	try {
		let user = uni.getStorageSync(USER_KEY);
		if (typeof user === "string" && user) {
			user = JSON.parse(user);
		}
		if (!user || typeof user !== "object") {
			return null;
		}
		if (!user.id && user.userId) {
			user.id = user.userId;
		}
		return user.id ? user : null;
	} catch (e) {
		return null;
	}
}

export function setUserInfo(user) {
	uni.removeStorageSync(LOGOUT_FLAG);
	uni.setStorageSync(USER_KEY, user || {});
	if (user?.id) {
		uni.setStorageSync(USER_ID_KEY, user.id);
	}
}

export function clearUserInfo() {
	uni.removeStorageSync(USER_KEY);
	uni.removeStorageSync(USER_ID_KEY);
	uni.setStorageSync(LOGOUT_FLAG, "1");
}

export function consumeLogoutFlag() {
	try {
		const flag = uni.getStorageSync(LOGOUT_FLAG);
		if (flag) {
			uni.removeStorageSync(LOGOUT_FLAG);
			return true;
		}
	} catch (e) {
		return false;
	}
	return false;
}

export function isLoggedIn() {
	return !!getUserInfo();
}
