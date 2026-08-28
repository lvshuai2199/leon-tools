<template>
	<view class="content">
		<view class="popup-content">
			<text class="page-title">生成注册码</text>
			<uni-forms :modelValue="regData">
				<uni-forms-item label="类型:" name="registrationType">
					<picker @change="onRegistrationTypeChange" :value="selectedRegistrationType" :range="registrationTypes">
						<view class="uni-input">{{ registrationTypes[selectedRegistrationType] || '请选择注册码类型' }}</view>
					</picker>
				</uni-forms-item>

				<uni-forms-item label="单月:" name="oneMonthValid">
					<text>{{ regData.oneMonthValid }}</text>
				</uni-forms-item>
				<uni-forms-item label="永久：" name="longTimeValid">
					<text>{{ regData.longTimeValid }}</text>
				</uni-forms-item>
				<uni-forms-item label="注册码:" name="regCode">
					<uni-easyinput v-model="regData.regCode" placeholder="输入注册码" maxlength="6" />
				</uni-forms-item>
			</uni-forms>
			<view class="button-group">
				<button class="btn-generate" :loading="loading" @click="tempCodeGene()">生成</button>
			</view>
		</view>
	</view>
</template>

<script>
	export default {
		data() {
			return {
				userId: '',
				loading: false,
				selectedRegistrationType: '',
				regData: {
					regCode: '',
					regCodeType: null,
					oneMonthValid: 'OneMonth',
					longTimeValid: 'Forever',
				},
				registrationTypes: ['焊接专机', '码垛专机'],
			};
		},
		onLoad() {
			uni.getStorage({
				key: 'userId',
				success: (res) => {
					this.userId = res.data || '';
				},
				fail: () => {
					this.userId = '';
					uni.navigateTo({
						url: '/pages/login/login'
					});
				}
			});
		},
		methods: {
			onRegistrationTypeChange(event) {
				this.selectedRegistrationType = event.detail.value;
				if (this.selectedRegistrationType === 0) {
					this.regData.regCodeType = 1;
				} else if (this.selectedRegistrationType === 1) {
					this.regData.regCodeType = 2;
				}
			},
			async tempCodeGene() {
				if (this.regData.regCodeType == null) {
					uni.showToast({
						title: '请选择注册码类型',
						icon: 'none'
					});
					return;
				}
				if (this.regData.regCode.length !== 6) {
					uni.showToast({
						title: '注册码长度必须为6位',
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
						if (response.data) {
							this.regData = { ...this.regData, ...response.data };
						}
						uni.showToast({
							title: '生成成功',
							icon: 'success'
						});
					} else {
						uni.showToast({
							title: '获取失败,' + (response.message || ''),
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
	}
</script>

<style>
	.content {
		padding: 10px;
	}

	.page-title {
		display: block;
		font-size: 18px;
		font-weight: bold;
		margin-bottom: 16px;
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

	.button-group {
		margin-top: 20px;
	}

	.btn-generate {
		width: 100%;
		padding: 10px;
		border: none;
		border-radius: 4px;
		background-color: #007AFF;
		color: white;
		font-size: 16px;
		font-weight: bold;
	}

	.uni-input {
		padding: 8px;
		border: 1px solid #ddd;
		border-radius: 4px;
		background-color: #f5f5f5;
	}
</style>
