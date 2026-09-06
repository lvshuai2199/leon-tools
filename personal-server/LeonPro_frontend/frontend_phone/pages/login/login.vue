<template>
	<view class="login-page">
		<view class="login-bg"></view>
		<view class="login-card">
			<view class="brand">
				<image class="logo" src="/static/logo.png" mode="aspectFit"></image>
				<text class="title">LeonPro</text>
				<text class="subtitle">个人工具平台</text>
			</view>

			<view class="field">
				<text class="label">用户名</text>
				<input
					v-model="form.username"
					class="input"
					placeholder="请输入用户名"
					confirm-type="next"
				/>
			</view>
			<view class="field">
				<text class="label">密码</text>
				<input
					v-model="form.password"
					class="input"
					password
					placeholder="请输入密码"
					confirm-type="done"
					@confirm="handleLogin"
				/>
			</view>

			<button class="submit" :loading="loading" :disabled="loading" @click="handleLogin">
				登 录
			</button>
		</view>
	</view>
</template>

<script>
	import { consumeLogoutFlag, getUserInfo, setUserInfo } from "@/utils/auth.js";

	export default {
		data() {
			return {
				loading: false,
				form: {
					username: "",
					password: "",
				},
			};
		},
		onShow() {
			if (consumeLogoutFlag()) {
				return;
			}
			if (getUserInfo()?.id) {
				uni.reLaunch({ url: "/pages/workspace/workspace" });
			}
		},
		methods: {
			validate() {
				if (!this.form.username.trim()) {
					uni.showToast({ title: "请输入用户名", icon: "none" });
					return false;
				}
				if (!this.form.password) {
					uni.showToast({ title: "请输入密码", icon: "none" });
					return false;
				}
				if (this.form.password.length < 6) {
					uni.showToast({ title: "密码长度不能少于6位", icon: "none" });
					return false;
				}
				return true;
			},
			async handleLogin() {
				if (!this.validate() || this.loading) return;
				this.loading = true;
				try {
					const data = await this.$api.login(this.form);
					if (!data || !data.username) {
						uni.showToast({ title: "登录失败，请检查用户名或密码", icon: "none" });
						return;
					}
					setUserInfo(data);
					uni.showToast({ title: "登录成功", icon: "success" });
					setTimeout(() => {
						uni.reLaunch({ url: "/pages/workspace/workspace" });
					}, 300);
				} catch (error) {
					console.error("登录失败", error);
				} finally {
					this.loading = false;
				}
			},
		},
	};
</script>

<style scoped>
	.login-page {
		min-height: 100vh;
		padding: calc(120rpx + env(safe-area-inset-top)) 48rpx 80rpx;
		box-sizing: border-box;
		background: linear-gradient(180deg, #1d4ed8 0%, #4080ff 42%, #f4f6fb 42%);
	}

	.login-card {
		padding: 56rpx 40rpx 48rpx;
		background: #fff;
		border-radius: 24rpx;
		box-shadow: 0 16rpx 48rpx rgba(29, 78, 216, 0.12);
	}

	.brand {
		display: flex;
		flex-direction: column;
		align-items: center;
		margin-bottom: 48rpx;
	}

	.logo {
		width: 112rpx;
		height: 112rpx;
		margin-bottom: 16rpx;
	}

	.title {
		font-size: 44rpx;
		font-weight: 700;
		color: #1f2937;
	}

	.subtitle {
		margin-top: 8rpx;
		font-size: 26rpx;
		color: #6b7280;
	}

	.field {
		margin-bottom: 28rpx;
	}

	.label {
		display: block;
		margin-bottom: 12rpx;
		font-size: 26rpx;
		color: #4b5563;
	}

	.input {
		height: 88rpx;
		padding: 0 24rpx;
		font-size: 30rpx;
		background: #f5f7fb;
		border: 1px solid #e5e7eb;
		border-radius: 16rpx;
	}

	.submit {
		margin-top: 20rpx;
		height: 92rpx;
		line-height: 92rpx;
		font-size: 32rpx;
		color: #fff;
		background: #4080ff;
		border: none;
		border-radius: 16rpx;
	}

	.submit::after {
		border: none;
	}
</style>
