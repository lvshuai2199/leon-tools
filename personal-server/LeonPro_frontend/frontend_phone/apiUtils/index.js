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

	listCrabShipments(params) {
		return http.get(`/crabShipment/getAll${toQuery(params)}`);
	},

	getCrabShipment(id) {
		return http.get(`/crabShipment/${encodeURIComponent(id)}`);
	},

	saveCrabShipment(data) {
		return http.post("/crabShipment/save", data);
	},

	updateCrabStatus(data) {
		return http.post("/crabShipment/status", data);
	},

	batchSaveCrabShipments(data) {
		return http.post("/crabShipment/batchSave", data);
	},

	parseCrabText(text) {
		return http.post("/crabShipment/parse", { text });
	},

	deleteCrabShipments(ids) {
		return http.post("/crabShipment/del", ids);
	},

	publicCrabShipment(publicId) {
		return http.get(`/public/crabShipment/${encodeURIComponent(publicId)}`);
	},
};

function toQuery(params) {
	const q = new URLSearchParams();
	Object.entries(params || {}).forEach(([key, value]) => {
		if (value === undefined || value === null || value === "") return;
		q.set(key, String(value));
	});
	const s = q.toString();
	return s ? `?${s}` : "";
}
