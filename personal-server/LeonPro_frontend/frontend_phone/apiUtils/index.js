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
		});
	},

	listRegCodeConfig() {
		return http.get("/regCodeConfig/list", currentUserQuery());
	},

	genTempRegCode(params) {
		return http.post("/auth/genTempRegCode", params);
	},

	myQuota() {
		return http.get("/regCodeUser/myQuota", currentUserQuery());
	},
};
