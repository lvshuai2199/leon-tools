//封装具体的接口调用
import http from '@/apiUtils/request.js'

export default {

	//登录接口
	syslogin(params) {
		console.log("进入登录接口，传入参数为：" + params)
		return http.post('/auth/login2', params)
	},

	// 获取验证码
	getcaptcha() {
		return http.post("/auth/captcha");
	},

	/**
	 * @param {Object} params
	 * 获取所有的用户信息
	 */
	getUserInfo(params) {
		console.log("进入获取信息接口")
		return http.get('/sysUsers/getUsers')
	},

	/**
	 * @param {Object} params
	 * 表单信息的增加（任务）
	 */
	getAllTasks(params) {
		console.log("获取任务列表接口，传入参数为：" + params.publisherId)
		console.log(params.size);
		// return http.post('/sysTasks/getAllMemos',params)
		return http.get('/sysTasks/getAll', params)
	},

	updateTask(params) {
		console.log("更新事项信息接口，传入参数为：" + params)
		return http.post('/sysTasks/update', params)
	},

	addTask(params) {
		console.log("新建事项信息接口，传入参数为：" + params)
		return http.post('/sysTasks/add', params)
	},

	submitErrorRemark(params) {
		console.log("增加异常信息填充，传入参数为：" + params)
		return http.post('/auth/abnormalUpload', params)
	},

	/**
	 * @param {Object} params
	 * 获取用户信息
	 */
	getAllRole(params) {
		console.log("进入获取角色接口")
		return http.get('/sysRoles/getAll', params)
	},

	addRole(params) {
		console.log("新建角色接口，传入参数为：" + params)
		return http.post('/sysRoles/add', params)
	},

	updateRole(params) {
		console.log("更新角色接口，传入参数为：" + params)
		return http.post('/sysRoles/update', params)
	},

	/**
	 * @param {Object} params
	 * 注册码申请表单增加
	 */
	getAllRegInfo(params) {
		console.log("获取所有的注册码申请信息")
		return http.get('/comRegistration/getAll', params)
	},
	addRegApply(params) {
		console.log("增加注册码申请，传入参数为：" + params)
		return http.post('/comRegistration/add', params)
	},
	getRegCode(params) {
		console.log("生成注册码：" + params)
		return http.post('/auth/getRegCode', params)
	},
	genTempRegCode(params) {
		console.log("生成临时注册码：" + params)
		return http.post('/auth/genTempRegCode', params)
	},
	
	getAllMessageInfo(params) {
		console.log("获取所有的信息")
		return http.get('/sysInfo/getAll', params)
	},
	updateMessageInfo(params) {
		console.log("更新信息查看情况接口，传入参数为：" + params)
		return http.post('/sysInfo/update', params)
	},
	
	/* 
		EXTERN
	 */
	getWalletList(params) {
		console.log("获取所有的账户列表")
		return http.get('/externWallet/getAll', params)
	},
	getWalletAccountList(params) {
		console.log("获取钱包详情")
		return http.get('/externAccounts/getAll',params)
	},
}