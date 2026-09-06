import http from "@/apiUtils/request.js";

export default {
	login(params) {
		return http.post("/auth/login2", {
			username: params.username,
			password: params.password,
		});
	},

	listRegCodeConfig() {
		return http.get("/regCodeConfig/list");
	},

	genTempRegCode(params) {
		return http.post("/auth/genTempRegCode", params);
	},

	myQuota() {
		return http.get("/regCodeUser/myQuota");
	},
};
