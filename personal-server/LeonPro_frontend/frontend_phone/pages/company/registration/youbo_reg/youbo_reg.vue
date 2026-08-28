<template>
	<view class="content">
		<!-- 密码验证弹窗 -->
		<uni-popup ref="passwordPopup" type="dialog">
			<view class="password-dialog">
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
				<text class="description">将限制机床部分功能使用，解锁使用所有功能</text>
				<button class="btn-unlock" @click="showPasswordDialog">解锁系统</button>
			</view>
		</view>

		<!-- 授权后显示主要内容 -->
		<view v-else>
			<!-- 用户头像部分 -->
			<view class="avatar-container">
				<view class="avatar-wrapper">
					<image 
						src="https://qqtx.apihz.cn/img/qqtx/79/8c2/8c2e160dd08cd8f59f59dbac7402675c.jpg" 
						class="avatar" 
						mode="aspectFill" 
					/>
				</view>
				<text class="avatar-title">友博--插件注册码生成</text>
			</view>

			<!-- 主要内容区域 -->
			<view class="popup-content">
				<uni-forms :modelValue="regData" :rules="rules">
					<!-- 前两个时间项 -->
					<uni-forms-item label="一个月:" name="oneMonthValid">
						<text>{{ regData.oneMonthValid }}</text>
					</uni-forms-item>

					<uni-forms-item label="两个月:" name="twoMonthValid">
						<text>{{ regData.twoMonthValid }}</text>
					</uni-forms-item>

					<!-- 展开/收起按钮 -->
					<view class="expand-button-wrapper">
						<text 
							class="expand-button"
							@click="toggleExpand"
						>
							{{ isExpanded ? '收起 ▲' : '展开更多 ▼' }}
						</text>
					</view>

					<!-- 展开的时间项 -->
					<view v-if="isExpanded" class="expanded-items">
						<uni-forms-item label="四个月:" name="fourMonthValid">
							<text>{{ regData.fourMonthValid }}</text>
						</uni-forms-item>

						<uni-forms-item label="六个月:" name="sixMonthValid">
							<text>{{ regData.sixMonthValid }}</text>
						</uni-forms-item>
						
						<uni-forms-item label="十三个月:" name="sixMonthValid">
							<text>{{ regData.thirteenMonthValid }}</text>
						</uni-forms-item>

						<uni-forms-item label="永久:" name="longTimeValid">
							<text>{{ regData.longTimeValid }}</text>
						</uni-forms-item>
					</view><uni-forms-item label="注册码:" name="regCode">
						<uni-easyinput 
							v-model="regData.regCode" 
							placeholder="输入注册码"
							maxlength="6"
						/>
					</uni-forms-item>

					<!-- 注册码类型选择 -->
					<uni-forms-item label="类型:" name="regCodeType">
						<view class="uni-input">
							CNC插件
						</view>
					</uni-forms-item>
				</uni-forms>

				<!-- 生成按钮 -->
				<button class="btn-generate" @click="tempCodeGene()" :loading="loading">
					{{ loading ? '生成中...' : '生成' }}
				</button>
			</view>
		</view>
	</view>
</template>

<script>
export default {
	data() {
		return {
			isAuthorized: false,
			isExpanded: false,
			password: '',
			loading: false,
			userId: '',
			selectedRegistrationType: 0,
			correctPassword: 'uber1802810', // 设置正确的密码
			regData: {
				regCode: '',
				regCodeType: null,
				oneMonthValid: 'OneMonth',
				twoMonthValid: 'TwoMonth',
				fourMonthValid: 'FourMonth',
				sixMonthValid: 'SixMonth',
				thirteenMonthValid: 'ThirteenMonth',
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

	onLoad() {
		// 页面加载时每次都显示密码输入框
		// this.showPasswordDialog();
	},

	onShow() {
		// 页面显示时也重置授权状态
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
			this.isAuthorized = false;
			// 延迟打开，确保页面已加载
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
				// 密码正确
				this.isAuthorized = true;
				this.$refs.passwordPopup.close();
				uni.showToast({
					title: '验证成功！',
					icon: 'success'
				});
			} else {
				// 密码错误
				uni.showToast({
					title: '校验失败，无权限',
					icon: 'error'
				});
				this.password = '';
			}
		},

		// 切换展开/收起
		toggleExpand() {
			this.isExpanded = !this.isExpanded;
		},

		// 注册码类型变化处理
		onRegistrationTypeChange(event) {
			this.selectedRegistrationType = event.detail.value;
			this.regData.regCodeType = this.selectedRegistrationType + 1;
		},

		// 生成临时注册码
		async tempCodeGene() {
			if (this.regData.regCode.length !== 6) {
				uni.showToast({
					title: '注册码长度必须为6位',
					icon: 'none'
				});
				return;
			}
			
			this.regData.regCodeType = 3;
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
	padding: 10px;
	min-height: 100vh;
}

/* 无权限容器 */
.unauthorized-container {
	display: flex;
	justify-content: center;
	align-items: center;
	min-height: 100vh;
	background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.unauthorized-content {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	background-color: white;
	padding: 40px 30px;
	border-radius: 12px;
	box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
	text-align: center;
	width: 85%;
	max-width: 400px;
}

.icon {
	font-size: 60px;
	margin-bottom: 20px;
}

.title {
	font-size: 24px;
	font-weight: bold;
	color: #333;
	margin-bottom: 10px;
}

.description {
	font-size: 14px;
	color: #666;
	margin-bottom: 30px;
	line-height: 1.5;
}

.btn-unlock {
	width: 100%;
	padding: 12px;
	background-color: #007AFF;
	color: white;
	border: none;
	border-radius: 6px;
	font-size: 16px;
	font-weight: bold;
}

.btn-unlock:active {
	background-color: #0051d5;
}

/* 密码对话框 */
.password-dialog {
	padding: 20px;
	background-color: white;
	border-radius: 8px;
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
	gap: 10px;
	margin-top: 20px;
}

.btn-cancel,
.btn-confirm {
	flex: 1;
	padding: 10px;
	border: none;
	border-radius: 4px;
	font-size: 14px;
	font-weight: bold;
}

.btn-cancel {
	background-color: #f0f0f0;
	color: #333;
}

.btn-confirm {
	background-color: #007AFF;
	color: white;
}

/* 头像容器 */
.avatar-container {
	display: flex;
	flex-direction: column;
	justify-content: center;
	align-items: center;
	padding: 15px 0;
}

/* 头像包装器 */
.avatar-wrapper {
	margin-bottom: 10px;
}

/* 圆形头像 - 缩小 */
.avatar {
	width: 80px;
	height: 80px;
	border-radius: 50%;
	border: 3px solid #007AFF;
	box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

/* 头像标题 */
.avatar-title {
	font-size: 16px;
	font-weight: bold;
	color: #333;
}

.popup-content {
	padding: 20px;
	background-color: white;
	width: 90%;
	max-width: 600px;
	border-radius: 8px;
	margin: 20px auto;
}

/* 展开按钮包装器 */
.expand-button-wrapper {
	display: flex;
	justify-content: center;
	padding: 8px 0;
	margin: 5px 0;
}

/* 展开按钮 - 改为文本链接风格 */
.expand-button {
	font-size: 13px;
	color: #007AFF;
	font-weight: 500;
	padding: 4px 8px;
	border-radius: 3px;
	transition: all 0.2s ease;
}

.expand-button:active {
	background-color: #f0f0f0;
	color: #0051d5;
}

/* 展开的项目容器 */
.expanded-items {
	animation: slideDown 0.3s ease-out;
}

@keyframes slideDown {
	from {
		opacity: 0;
		transform: translateY(-10px);
	}
	to {
		opacity: 1;
		transform: translateY(0);
	}
}

.btn-generate {
	width: 100%;
	padding: 12px;
	margin-top: 20px;
	background-color: #007AFF;
	color: white;
	border: none;
	border-radius: 4px;
	font-size: 16px;
	font-weight: bold;
}

.btn-generate:active {
	background-color: #0051d5;
}

.uni-input {
	padding: 8px;
	border: 1px solid #ddd;
	border-radius: 4px;
	background-color: #f5f5f5;
}
</style>
