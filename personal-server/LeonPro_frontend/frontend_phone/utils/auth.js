const USER_KEY = "userInfo";
const USER_ID_KEY = "userId";

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
		return user.id || user.username ? user : null;
	} catch (e) {
		return null;
	}
}

export function setUserInfo(user) {
	uni.setStorageSync(USER_KEY, user || {});
	if (user?.id) {
		uni.setStorageSync(USER_ID_KEY, user.id);
	}
}

export function clearUserInfo() {
	uni.removeStorageSync(USER_KEY);
	uni.removeStorageSync(USER_ID_KEY);
}

export function isLoggedIn() {
	return !!getUserInfo();
}
