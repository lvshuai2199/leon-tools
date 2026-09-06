import http from "@/apiUtils/request.js";
import { getUserInfo } from "@/utils/auth.js";

function currentUserQuery() {
	const user = getUserInfo() || {};
	return {
		userId: user.id || undefined,
		username: user.username || undefined,
	};
}

export default {
	login(params) {
		return http.post("/auth/login2", {
			username: params.username,
			password: params.password,
			source: "app",
		});
	},

	listRegCodeConfig() {
		return http.post("/regCodeConfig/available", currentUserQuery());
	},

	genTempRegCode(params) {
		return http.post("/auth/genTempRegCode", params);
	},

	myQuota() {
		return http.post("/regCodeUser/myQuota", currentUserQuery());
	},
};
