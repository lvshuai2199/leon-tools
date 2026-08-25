const http = {
	// baseUrl: "http://127.0.0.1:8089",
	// 华为云公网
	baseUrl: "http://106.12.12.111:8089",

	// 请求方法
	request(config) {
		// 处理请求配置
		config = beforeRequest(config);
		config.url = this.baseUrl + config.url;
		console.log(config.url);

		// 如果是 POST 请求且没有指定 Content-Type，设置为 application/x-www-form-urlencoded
		if (config.method === "POST" && !config.header) {
			// config.header = { 'Content-Type': 'application/x-www-form-urlencoded' };
			config.header = {
				'Content-Type': 'application/json'
			};
		}

		// 创建一个 Promise 对象
		return new Promise((resolve, reject) => {
			uni.request({
				...config,
				success: (res) => {
					let response = beforeResponse(res);
					resolve(response);
				},
				fail: (err) => {
					errorHandle(err);
					reject(err);
				}
			});
		});
	},

	// GET 请求
	get(url, data, auth) {
		return this.request({
			url: url,
			data: data,
			auth: auth,
			method: "GET"
		});
	},

	// POST 请求
	post(url, data, auth) {
		return this.request({
			url: url,
			data: data,
			auth: auth,
			method: "POST"
		});
	}
};

// 请求拦截器
const beforeRequest = (config) => {
	console.log('请求拦截器', config);
	// 在这里添加认证信息或其他请求配置
	return config;
};

// 响应拦截器
const beforeResponse = (response) => {
	console.log('响应拦截器', response);
	// 在这里处理响应数据

	console.log("响应拦截器数据")
	console.log(response)
	if (response.statusCode === 200) {
		response = response.data
		// this.messageToggle('success','Success');
	}
	console.log("响应拦截器返回数据")
	console.log(response)
	return response;
};

// 异常处理器
const errorHandle = (err) => {
	console.log('网络异常，请求失败', err);
	// 在这里处理异常情况
	return err;
};

export default http;