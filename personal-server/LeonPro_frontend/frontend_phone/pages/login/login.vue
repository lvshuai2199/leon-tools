<template>
	<view>
		<!-- 显示用户信息 -->
		<uni-notice-bar single text="使用手机号＋密码进行登录,初始密码为123456" v-if="!userInfo" />

		<view class="content">
			<image class="logo" src="/static/logo.png"></image>
			<!-- 登录表单 -->
			<uni-forms ref="loginForm" :modelValue="loginInfo" :rules="rules">
				<uni-forms-item label="账号:" name="phone">
					<uni-easyinput v-model="loginInfo.username" placeholder="请输入账号" />
				</uni-forms-item>
				<uni-forms-item label="密码:" name="password">
					<uni-easyinput type="password" v-model="loginInfo.password" placeholder="请输入密码" />
				</uni-forms-item>
				<uni-forms-item label="验证码:" name="captcha">
					<uni-easyinput v-model="loginInfo.captchaCode" placeholder="请输入验证码" />
				</uni-forms-item>
				<!-- 显示验证码图片 -->
				<image v-if="loginInfo.captchaBase64" :src="loginInfo.captchaBase64" class="captcha-image"
					mode="aspectFit" />
			</uni-forms>
			<button type="default" @click="login">登录</button>
			<navigator url="/pages/login/register" hover-class="other-navigator-hover">
				<button type="default">注册</button>
			</navigator>
		</view>

		<!-- 提示信息弹窗 -->
		<uni-popup ref="message" type="message">
			<uni-popup-message :type="msgType" :message="messageText" :duration="2000"></uni-popup-message>
		</uni-popup>
	</view>
</template>

<script>
	import {
		mapActions
	} from 'vuex';

	export default {
		data() {
			return {
				loginInfo: {
					username: null,
					password: null,
					captchaKey: "abc",
					captchaBase64: ''
				},
				userInfo: null,
				msgType: null,
				messageText: null,
				rules: {
					phone: {
						required: true,
						message: '账号不能为空',
					},
					password: {
						required: true,
						message: '密码不能为空',
					},
					captcha: {
						required: true,
						message: '验证码不能为空',
					},
				}
			};
		},
		onLoad() {
			this.getCaptcha();
		},
		methods: {
			...mapActions(['saveUserId', 'saveRoleId']),
			async getCaptcha() {
				const response = await this.$api.getcaptcha();
				if (response.status === 200) {
					this.loginInfo.captchaKey = response.data.captchaKey;
					this.loginInfo.captchaBase64 = `data:image/png;base64,${response.data.captchaBase64}`;
				} else {
					this.messageToggle('error', '数据获取失败');
				}
			},
			async messageToggle(type, content) {
				this.msgType = type;
				this.messageText = content;
				this.$refs.message.open();
			},
			async login() {
				try {
					await this.$refs.loginForm.validate(); // 验证表单
					const response = await this.$api.syslogin(this.loginInfo);
					if (response.status === 200) {
						const userId = response.data?.id; // 获取用户 ID
						const roleId = response.data?.roleId; // 获取用户 ID
						if (userId) {
							// 存储用户 ID
							uni.setStorage({
								key: 'userId',
								data: userId, // 将 'USER_ID_VALUE' 替换为实际的用户 ID
								success: function() {
									console.log('用户 ID 存储成功');
								},
								fail: function() {
									console.log('用户 ID 存储失败');
								}
							});
							console.log('存储localstorage成功！');
							const fetchData = () => {
								// 使用箭头函数，保持 `this` 指向
								this.saveUserId(userId); // 调用 Vuex action 保存用户 ID
								this.saveRoleId(roleId);
							};
							console.log('用户 ID 已保存到 Vuex:', userId);
							uni.showToast({
								title: '登录成功',
								icon: 'success'
							});
							uni.switchTab({
								url: '/pages/index/index'
							});
						} else {
							this.messageToggle('error', '用户 ID 不存在');
						}
					} else {
						this.messageToggle('error', '登陆失败，请检查您的账号及密码是否正确');
					}
				} catch (error) {
					console.log(error); // 打印错误信息
					this.messageToggle('error', '表单验证失败');
				}
			}
		}
	}
</script>

<style>
	.content {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
	}

	.logo {
		height: 200rpx;
		width: 200rpx;
		margin-bottom: 10px;
	}

	button {
		margin-top: 5rpx;
		background-color: #3d4f7f;
		color: #fff;
		padding: 10rpx;
		border-radius: 5rpx;
	}

	.captcha-image {
		margin-top: 10rpx;
		height: 60rpx;
		margin-bottom: 10rpx;
	}
</style>