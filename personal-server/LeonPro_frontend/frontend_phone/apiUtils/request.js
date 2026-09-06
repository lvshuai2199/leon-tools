import { getUserInfo } from "@/utils/auth.js";

const http = {
	// #ifdef H5
	baseUrl: "/prod-api",
	// #endif
	// #ifndef H5
	baseUrl: "http://124.220.57.33:8089",
	// #endif

	request(config) {
		config = beforeRequest(config);
		config.url = this.baseUrl + config.url;

		if (config.method === "POST" && !config.header["Content-Type"]) {
			config.header["Content-Type"] = "application/json";
		}

		return new Promise((resolve, reject) => {
			uni.request({
				...config,
				success: (res) => {
					try {
						const data = unwrapResponse(res);
						resolve(data);
					} catch (err) {
						reject(err);
					}
				},
				fail: (err) => {
					uni.showToast({
						title: "网络异常，请稍后重试",
						icon: "none",
					});
					reject(err);
				},
			});
		});
	},

	get(url, data) {
		return this.request({
			url,
			data,
			method: "GET",
		});
	},

	post(url, data) {
		return this.request({
			url,
			data,
			method: "POST",
		});
	},
};

function beforeRequest(config) {
	config.header = config.header || {};
	const user = getUserInfo();
	if (user?.id) {
		config.header["X-User-Id"] = String(user.id);
	}
	if (user?.username) {
		config.header["X-Username"] = encodeURIComponent(user.username);
	}
	return config;
}

function unwrapResponse(response) {
	if (response.statusCode !== 200) {
		uni.showToast({
			title: "请求失败",
			icon: "none",
		});
		throw new Error("HTTP " + response.statusCode);
	}

	const body = response.data;
	if (!body || typeof body !== "object" || !("status" in body)) {
		return body;
	}

	if (String(body.status) === "200") {
		return body.data;
	}

	uni.showToast({
		title: body.message || "请求失败",
		icon: "none",
	});
	throw new Error(body.message || "请求失败");
}

export default http;
