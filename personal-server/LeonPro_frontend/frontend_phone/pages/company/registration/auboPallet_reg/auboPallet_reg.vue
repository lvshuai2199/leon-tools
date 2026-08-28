<template>
	<view class="content">
		<!-- 密码验证弹窗 -->
		<uni-popup ref="passwordPopup" type="dialog">
			<view class="password-dialog">
				<view class="dialog-icon">🔐</view>
				<view class="dialog-title">请输入访问密码</view>
				<uni-easyinput 
					v-model="password" 
					type="password"
					placeholder="请输入密码"
					@keyup.enter="verifyPassword"
				/>
				<view class="dialog-buttons">
					<button class="btn-cancel" @click="$refs.passwordPopup.close()">取消</button>
					<button class="btn-confirm" @click="verifyPassword">确认</button>
				</view>
			</view>
		</uni-popup>

		<!-- 无权限提示 -->
		<view v-if="!isAuthorized" class="unauthorized-container">
			<view class="unauthorized-content">
				<text class="icon">🔒</text>
				<text class="title">系统未解密</text>
				<text class="description">请输入密码解锁码垛系统</text>
				<button class="btn-unlock" @click="showPasswordDialog">
					<text class="btn-icon">🔓</text>
					<text>解锁系统</text>
				</button>
			</view>
		</view>

		<!-- 授权后显示主要内容 -->
		<view v-else class="main-container">
			<!-- 头部卡片 -->
			<view class="header-card">
				<view class="avatar-section">
					<image 
						src="https://qqtx.apihz.cn/img/qqtx/79/8c2/8c2e160dd08cd8f59f59dbac7402675c.jpg" 
						class="avatar" 
						mode="aspectFill" 
					/>
					<view class="user-info">
						<text class="user-name">码垛系统</text>
						<text class="user-desc">注册码生成工具</text>
					</view>
				</view>
			</view>

			<!-- 表单卡片 -->
			<view class="form-card">
				<view class="card-title">
					<text class="title-icon">⚙️</text>
					<text>注册信息</text>
				</view>
				
				<uni-forms :modelValue="regData" :rules="rules">
					<!-- 有效期类型 -->
					<view class="validity-section">
						<view class="section-label">有效期类型</view>
						<view class="validity-options">
							<view class="validity-item">
								<text class="validity-label">一个月</text>
								<text class="validity-value">{{ regData.oneMonthValid }}</text>
							</view>
							<view class="validity-item">
								<text class="validity-label">永久</text>
								<text class="validity-value">{{ regData.longTimeValid }}</text>
							</view>
						</view>
					</view>

					<!-- 注册码输入 -->
					<uni-forms-item label="注册码" name="regCode" required>
						<uni-easyinput 
							v-model="regData.regCode" 
							placeholder="请输入6位注册码"
							maxlength="6"
							:clearable="true"
						/>
					</uni-forms-item>

					<!-- 设备类型 -->
					<uni-forms-item label="设备类型" name="regCodeType">
						<view class="device-type">
							<text class="device-icon">🤖</text>
							<text>码垛专机</text>
						</view>
					</uni-forms-item>
				</uni-forms>

				<!-- 生成按钮 -->
				<button 
					class="btn-generate" 
					@click="tempCodeGene()" 
					:loading="loading"
					:disabled="loading"
				>
					<text v-if="!loading" class="btn-icon">✨</text>
					<text>{{ loading ? '生成中...' : '生成注册码' }}</text>
				</button>
			</view>

			<!-- 底部提示 -->
			<view class="footer-tip">
				<text class="tip-icon">💡</text>
				<text class="tip-text">请确保注册码为6位字符</text>
			</view>
		</view>
	</view>
</template>

<script>
export default {
	data() {
		return {
			isAuthorized: false,
			password: '',
			loading: false,
			userId: '',
			correctPassword: 'ssnb666',
			regData: {
				regCode: '',
				regCodeType: 2,
				oneMonthValid: 'OneMonth',
				longTimeValid: 'Forever',
			},
			rules: {
				regCode: {
					rules: [
						{ required: true, errorMessage: '注册码不能为空' },
						{ minLength: 6, maxLength: 6, errorMessage: '注册码长度必须为6位' }
					]
				}
			}
		};
	},

	onShow() {
		this.isAuthorized = false;
		this.password = '';
		uni.getStorage({
			key: 'userId',
			success: (res) => {
				this.userId = res.data || '';
			},
			fail: () => {
				this.userId = '';
			}
		});
	},

	methods: {
		// 显示密码对话框
		showPasswordDialog() {
			this.password = '';
			setTimeout(() => {
				this.$refs.passwordPopup.open();
			}, 100);
		},

		// 验证密码
		verifyPassword() {
			if (!this.password) {
				uni.showToast({
					title: '请输入密码',
					icon: 'none'
				});
				return;
			}

			if (this.password === this.correctPassword) {
				this.isAuthorized = true;
				this.$refs.passwordPopup.close();
				uni.showToast({
					title: '验证成功！',
					icon: 'success'
				});
			} else {
				uni.showToast({
					title: '密码错误，无权限访问',
					icon: 'error'
				});
				this.password = '';
			}
		},

		// 生成临时注册码
		async tempCodeGene() {
			// 验证注册码
			if (!this.regData.regCode || this.regData.regCode.length !== 6) {
				uni.showToast({
					title: '请输入6位注册码',
					icon: 'none'
				});
				return;
			}
			
			this.loading = true;
			try {
				const response = await this.$api.genTempRegCode({
					...this.regData,
					applyId: this.userId
				});
				if (response.status === 200) {
					uni.showToast({
						title: '生成成功！',
						icon: 'success'
					});
					if (response.data) {
						this.regData = { ...this.regData, ...response.data };
					}
				} else {
					uni.showToast({
						title: response.message || '生成失败',
						icon: 'none'
					});
				}
			} catch (error) {
				console.error('生成注册码失败:', error);
				uni.showToast({
					title: '生成失败，请重试',
					icon: 'none'
				});
			} finally {
				this.loading = false;
			}
		}
	}
};
</script>

<style scoped>
.content {
	min-height: 100vh;
	background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

/* ========== 无权限页面 ========== */
.unauthorized-container {
	display: flex;
	justify-content: center;
	align-items: center;
	min-height: 100vh;
	padding: 20px;
}

.unauthorized-content {
	display: flex;
	flex-direction: column;
	align-items: center;
	background-color: white;
	padding: 50px 30px;
	border-radius: 20px;
	box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
	width: 100%;
	max-width: 400px;
	animation: fadeInUp 0.5s ease-out;
}

@keyframes fadeInUp {
	from {
		opacity: 0;
		transform: translateY(30px);
	}
	to {
		opacity: 1;
		transform: translateY(0);
	}
}

.icon {
	font-size: 80px;
	margin-bottom: 20px;
	animation: pulse 2s infinite;
}

@keyframes pulse {
	0%, 100% {
		transform: scale(1);
	}
	50% {
		transform: scale(1.1);
	}
}

.title {
	font-size: 26px;
	font-weight: bold;
	color: #333;
	margin-bottom: 10px;
}

.description {
	font-size: 15px;
	color: #666;
	margin-bottom: 40px;
	text-align: center;
}

.btn-unlock {
	width: 100%;
	padding: 15px;
	background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
	color: white;
	border: none;
	border-radius: 12px;
	font-size: 16px;
	font-weight: bold;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 8px;
	box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
	transition: all 0.3s ease;
}

.btn-unlock:active {
	transform: translateY(2px);
	box-shadow: 0 2px 10px rgba(102, 126, 234, 0.4);
}

.btn-icon {
	font-size: 18px;
}

/* ========== 密码对话框 ========== */
.password-dialog {
	padding: 30px 20px;
	background-color: white;
	border-radius: 16px;
	min-width: 280px;
}

.dialog-icon {
	font-size: 50px;
	text-align: center;
	margin-bottom: 15px;
}

.dialog-title {
	font-size: 18px;
	font-weight: bold;
	margin-bottom: 20px;
	text-align: center;
	color: #333;
}

.dialog-buttons {
	display: flex;
	gap: 12px;
	margin-top: 20px;
}

.btn-cancel,
.btn-confirm {
	flex: 1;
	padding: 12px;
	border: none;
	border-radius: 8px;
	font-size: 15px;
	font-weight: bold;
	transition: all 0.3s ease;
}

.btn-cancel {
	background-color: #f5f5f5;
	color: #666;
}

.btn-cancel:active {
	background-color: #e0e0e0;
}

.btn-confirm {
	background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
	color: white;
}

.btn-confirm:active {
	opacity: 0.8;
}

/* ========== 主容器 ========== */
.main-container {
	min-height: 100vh;
	padding: 20px;
	animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
	from {
		opacity: 0;
	}
	to {
		opacity: 1;
	}
}

/* ========== 头部卡片 ========== */
.header-card {
	background: white;
	border-radius: 16px;
	padding: 20px;
	margin-bottom: 20px;
	box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.avatar-section {
	display: flex;
	align-items: center;
	gap: 15px;
}

.avatar {
	width: 60px;
	height: 60px;
	border-radius: 50%;
	border: 3px solid #667eea;
	box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.user-info {
	display: flex;
	flex-direction: column;
	gap: 5px;
}

.user-name {
	font-size: 20px;
	font-weight: bold;
	color: #333;
}

.user-desc {
	font-size: 13px;
	color: #999;
}

/* ========== 表单卡片 ========== */
.form-card {
	background: white;
	border-radius: 16px;
	padding: 20px;
	box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.card-title {
	display: flex;
	align-items: center;
	gap: 8px;
	font-size: 18px;
	font-weight: bold;
	color: #333;
	margin-bottom: 20px;
	padding-bottom: 15px;
	border-bottom: 2px solid #f0f0f0;
}

.title-icon {
	font-size: 20px;
}

/* 有效期部分 */
.validity-section {
	margin-bottom: 20px;
}

.section-label {
	font-size: 14px;
	color: #666;
	margin-bottom: 12px;
	font-weight: 500;
}

.validity-options {
	display: flex;
	gap: 12px;
}

.validity-item {
	flex: 1;
	background: linear-gradient(135deg, #667eea15 0%, #764ba215 100%);
	padding: 15px;
	border-radius: 12px;
	display: flex;
	flex-direction: column;
	gap: 8px;
	border: 2px solid #667eea30;
}

.validity-label {
	font-size: 13px;
	color: #666;
}

.validity-value {
	font-size: 15px;
	font-weight: bold;
	color: #667eea;
}

/* 设备类型 */
.device-type {
	display: flex;
	align-items: center;
	gap: 10px;
	padding: 12px 15px;
	background-color: #f8f9fa;
	border-radius: 8px;
	border: 1px solid #e0e0e0;
}

.device-icon {
	font-size: 20px;
}

/* 生成按钮 */
.btn-generate {
	width: 100%;
	padding: 15px;
	margin-top: 25px;
	background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
	color: white;
	border: none;
	border-radius: 12px;
	font-size: 16px;
	font-weight: bold;
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 8px;
	box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
	transition: all 0.3s ease;
}

.btn-generate:active:not([disabled]) {
	transform: translateY(2px);
	box-shadow: 0 2px 10px rgba(102, 126, 234, 0.4);
}

.btn-generate[disabled] {
	opacity: 0.6;
}

/* ========== 底部提示 ========== */
.footer-tip {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 8px;
	margin-top: 20px;
	padding: 12px;
	background: rgba(255, 255, 255, 0.2);
	border-radius: 12px;
	backdrop-filter: blur(10px);
}

.tip-icon {
	font-size: 16px;
}

.tip-text {
	font-size: 13px;
	color: white;
}
</style>
