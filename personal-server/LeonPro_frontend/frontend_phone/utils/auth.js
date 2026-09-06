const USER_KEY = "userInfo";
const USER_ID_KEY = "userId";

export function getUserInfo() {
	try {
		const user = uni.getStorageSync(USER_KEY);
		return user && user.id ? user : null;
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
