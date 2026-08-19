<template>
	<view>
		<uni-notice-bar single text="填写对应的用户信息进行注册" />
		<uni-section title="用户注册" type="line">
			<view>
				<!-- 基础表单校验 -->
				<uni-forms ref="valiForm" :modelValue="regInfo" :rules="rules">
					<uni-forms-item>
						<image :src="regInfo.avatarUrl" class="logo" mode="aspectFill" />
						<p>{{regInfo.nickName}}</p>
					</uni-forms-item>
					<uni-forms-item label="姓名" required name="name">
						<uni-easyinput v-model="regInfo.username" placeholder="请输入姓名" />
					</uni-forms-item>
					<!-- <uni-forms-item label="年龄" required name="age">
						<uni-easyinput v-model="regInfo.age" placeholder="请输入年龄" />
					</uni-forms-item> -->
					
					<uni-forms-item label="手机号" required name="phoneNumber">
						<uni-easyinput v-model="regInfo.phone" placeholder="请输入手机号" />
					</uni-forms-item>
				</uni-forms>
				<button type="primary" @click="submit">提交</button>
				<button type="primary" @click="getUserInfo">获取用户信息</button>
				<button type="default" open-type="getPhoneNumber" @getphonenumber="decryptPhoneNumber">获取手机号</button>
				
			</view>
		</uni-section>
	</view>
</template>

<script>
	export default {
		data() {
			return {
				regInfo: {
					avatarUrl:'/static/logo.png',
					nickname: '微信用户',
					username: '',
					phone: '',
				},
				rules: {
					username: { required: true, message: '姓名不能为空' },
					phone: { required: true, message: '手机号不能为空', pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号' }
				}
			}
		},
		onLoad() {
			
		},
		methods: {
			// 获取微信用户的头像和昵称
			async getUserInfo() {
				try {
					const res = await wx.getUserProfile({
						desc: '用于完善会员资料'
					});
					console.log('用户信息：', res.userInfo);
					this.regInfo = res.userInfo; // 保存用户信息到 data 中
				} catch (err) {
					console.error('获取用户信息失败', err);
				}
			},
			async submit() {
				try {
					await this.$refs.valiForm.validate();
					
					const res = await wx.login();
					console.log('微信登录返回的 code:', res.code);
					this.regInfo.code = res.code;
					
					// 校验通过，提交表单数据到服务器
					//通过接口进行用户注册
					const response = await this.$api.wxRegister(this.regInfo);
					
					if (response.statusCode === 200) {
						uni.showToast({
							title: '注册成功',
							icon: 'success'
						});
					} else {
						uni.showToast({
							title: '注册失败',
							icon: 'none'
						});
					}
				} catch (error) {
					uni.showToast({
						title: '校验失败或请求错误',
						icon: 'none'
					});
					console.error('提交失败:', error);
				}
			}
		}
	}
</script>

<style>
	/* Add your styles here */
	.logo {
		border-radius: 50%;
		margin: 0 auto; /* 中央对齐 */
		display: block;
		height: 200rpx;
		width: 200rpx;
	}
</style>
