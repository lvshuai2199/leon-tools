<template>
	<view class="form-container">
		<uni-forms ref="applicationForm" :modelValue="formData" :rules="rules">
			<uni-forms-item label="申请人名称:" name="applyName">
				<uni-easyinput v-model="formData.applyName" placeholder="请输入申请人名称" />
			</uni-forms-item>
			<uni-forms-item label="公司:" name="company">
				<uni-easyinput v-model="formData.company" placeholder="请输入公司名称" />
			</uni-forms-item>
			<uni-forms-item label="对接销售:" name="salesName">
				<uni-easyinput v-model="formData.salesName" placeholder="请输入对接销售的姓名" />
			</uni-forms-item>
			<uni-forms-item label="联系方式:" name="applyPhone">
				<uni-easyinput v-model="formData.applyPhone" placeholder="请输入联系方式" />
			</uni-forms-item>
			<uni-forms-item label="注册码类型:" name="registrationType">
				<picker @change="onRegistrationTypeChange" :value="selectedRegistrationType" :range="registrationTypes">
					<view class="uni-input">{{ registrationTypes[selectedRegistrationType] || '请选择注册码类型' }}</view>
				</picker>
			</uni-forms-item>
			<uni-forms-item label="注册码:" name="regCode">
				<uni-easyinput v-model="formData.regCode" placeholder="自动填充注册码" readonly />
			</uni-forms-item>
			<uni-forms-item label="备注:" name="remarks">
				<uni-easyinput v-model="formData.remarks" placeholder="占位" type="textarea" />
			</uni-forms-item>
			<button type="default" @click="submitForm">提交</button>
		</uni-forms>
	</view>
</template>

<script>
	export default {
		data() {
			return {
				userId: '',
				formData: {
					applyName: '',
					company: '',
					salesName: '',
					applyPhone: '',
					regCode: '',
					regCodeType: null,
					remarks: '',
					applyStatus: 0
				},
				regCodeType: '',
				registrationTypes: ['焊接专机', '码垛专机'], // 下拉框选项

				rules: {
					applyName: {
						required: true,
						message: '请填写申请人名称'
					},
					company: {
						required: true,
						message: '请填写公司名称'
					},
					salesName: {
						required: true,
						message: '请填写对接销售'
					},
					applyPhone: {
						required: true,
						message: '请填写联系方式'
					},
					regCode: {
						required: true,
						message: '请填写注册码'
					}
				}
			};
		},
		onLoad() {
			// 获取用户 ID
			uni.getStorage({
				key: 'userId',
				success: (res) => { // 使用箭头函数
					console.log('用户 ID:', res.data);
					this.userId = res.data;
					console.log("set userId ");
				},
				fail: () => {
					console.log('获取用户 ID 失败');
					// 如果未登录，重定向到登录页面
					uni.navigateTo({
						url: '/pages/login/login'
					});
				}
			});
		},
		methods: {
			// 处理注册码类型变化
			onRegistrationTypeChange(event) {
				this.selectedRegistrationType = event.detail.value; // 更新选中的索引
				if (this.selectedRegistrationType === 0) {
					this.formData.regCodeType = 1; // 焊接专机对应的注册码
				} else if (this.selectedRegistrationType === 1) {
					this.formData.regCodeType = 2; // 码垛专机对应的注册码
				}
			},
			async submitForm() {
			    try {
			        await this.$refs.applicationForm.validate(); // 验证表单
					
					// 合并注册码和注册码类型
					// const registrationCode = this.formData.regCode + this.regCodeType;
					// this.formData.regCode = registrationCode; // 更新为最终的注册码
					
			        
			        // 检查注册码长度是否为6位
			        if (this.formData.regCodeType == null) {
			            uni.showToast({
			                title: '请选择注册码类型',
			                icon: 'none' // 使用 'none' 表示错误提示
			            });
			            return; // 结束方法执行
			        }
					// 检查注册码长度是否为6位
					if (this.formData.regCode.length !== 6) {
					    uni.showToast({
					        title: '注册码长度必须为6位',
					        icon: 'none' // 使用 'none' 表示错误提示
					    });
					    return; // 结束方法执行
					}
					
					this.formData.applyId = this.userId;
			        console.log('提交的数据:', this.formData);
			
			        const response = await this.$api.addRegApply(this.formData);
			        if (response.status === 200) {
			            uni.showToast({
			                title: '提交成功',
			                icon: 'success'
			            });
			            this.resetForm();
			            uni.navigateBack();
			        } else {
			            uni.showToast({
			                title: '提交失败，请重试',
			                icon: 'none'
			            });
			        }
			    } catch (error) {
			        console.error('提交失败:', error);
			    }
			},
			resetForm() {
				this.formData = {
					applyName: '',
					company: '',
					salesName: '',
					applyPhone: '',
					regCode: '',
					remarks: ''
				};
				this.selectedRegistrationType = ''; // 重置选择的注册码类型
				this.$refs.applicationForm.resetFields(); // 重置表单
			}
		}
	};
</script>

<style>
	.form-container {
		padding: 20rpx;
	}

	button {
		margin-top: 20rpx;
		background-color: #3d4f7f;
		color: #fff;
		padding: 10rpx;
		border-radius: 5rpx;
		border: none;
	}
</style>