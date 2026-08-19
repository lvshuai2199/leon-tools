<template>
	<view>
		<!-- 显示用户信息 -->
		<uni-notice-bar single text="请先进行登录!" v-if="!userInfo" />

		<view class="content">
			<!-- 用户信息部分 -->
			<view v-if="userInfo">
				<image :src="userInfo.avatarUrl" class="avatar" mode="aspectFill" />
				<text class="nickname">{{ userInfo.nickname }}</text>
			</view>
			<view v-else>
				<text class="login-prompt">请先进行登录！</text>
				<navigator url="/pages/login/login" hover-class="navigator-hover" class="login-button">
					<uni-icons type="person" size="60"></uni-icons>
					<text class="text">登录</text>
				</navigator>
			</view>
		</view>

		<uni-section title="基础样式" type="line" padding>
			<view class="grid-container">
				<uni-grid :column="2">
					<uni-grid-item v-for="(item, index) in menuItems" :key="index" class="grid-item">
						<navigator :url="item.url" hover-class="navigator-hover" class="navigator-item">
							<uni-icons :type="item.icon" size="40" class="icon"></uni-icons>
							<br />
							<text class="text">{{ item.label }}</text>
						</navigator>
					</uni-grid-item>
					<uni-grid-item v-if="userInfo" class="grid-item">
						<navigator @click="loginOut" hover-class="navigator-hover" class="navigator-item">
							<uni-icons type="help" size="40" class="icon"></uni-icons>
							<br />
							<text class="text">退出登录</text>
						</navigator>
					</uni-grid-item>
				</uni-grid>
			</view>
		</uni-section>

		<!-- 提示信息弹窗 -->
		<uni-popup ref="message" type="message">
			<uni-popup-message :type="msgType" :message="messageText" :duration="2000"></uni-popup-message>
		</uni-popup>
	</view>
</template>

<script>
	export default {
		data() {
			return {
				userInfo: {
					id:''
				},
				msgType: null,
				messageText: null,
				menuItems: [{
						url: '/pages/user/userList',
						icon: 'person',
						label: '用户管理'
					},
					{
						url: '/pages/user/role',
						icon: 'vip',
						label: '权限管理'
					},
					// {
					// 	url: '/pages/menu/menu',
					// 	icon: 'cart',
					// 	label: '菜单管理'
					// },
					// {
					// 	url: '/pages/link/myLink',
					// 	icon: 'paperclip',
					// 	label: '我的链接'
					// },
					{
						url: '/pages/registration/registration',
						icon: 'flag',
						label: '注册码'
					},
					{
						url: '/pages/company/registration/youbo_reg/youbo_reg',
						icon: 'flag',
						label: '注册码（友博）'
					},
					{
						url: '/pages/company/registration/auboPallet_reg/auboPallet_reg',
						icon: 'flag',
						label: '注册码（码垛）'
					},
					{
						url: '/pages/user/messagePage',
						icon: 'chat',
						label: '消息'
					},
					{
						url: '/pages/extern/walletList',
						icon: 'paperclip',
						label: '我的钱包'
					},
				]
			}
		},
		onLoad() {

			// 获取用户 ID
			uni.getStorage({
				key: 'userId',
				success: (res) => { // 使用箭头函数
					console.log('用户 ID:', res.data);
					this.userInfo.id = res.data;
					console.log("set userId " + this.userInfo.id);
					this.getCurUser();
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
			async messageToggle(type, content) {
				this.msgType = type;
				this.messageText = content;
				this.$refs.message.open();
			},
			async getCurUser() {
				try {
					const response = await this.$api.getUserInfo(this.userInfo);
					if (response.data && response.data.records.length > 0) {
						this.userInfo = response.data.records[0];
					}
				} catch (error) {
					this.messageToggle('error', '请先进行登录');
				}
			},
			async loginOut() {
				this.userInfo = null;
				this.messageToggle('success', '已成功退出登录');
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
		padding: 20rpx;
		/* 内边距 */
	}

	.avatar {
		border-radius: 50%;
		/* 圆形头像 */
		width: 80rpx;
		/* 缩小头像 */
		height: 80rpx;
		margin-bottom: 10rpx;
	}

	.nickname {
		font-size: 20rpx;
		/* 缩小昵称大小 */
		color: #333;
		/* 更深的颜色 */
	}

	.login-prompt {
		font-size: 18rpx;
		/* 缩小提示文字大小 */
		color: #ff0000;
		/* 红色提示 */
		margin-bottom: 10rpx;
	}

	.login-button {
		display: flex;
		align-items: center;
		justify-content: center;
		background-color: #3d4f7f;
		color: #fff;
		padding: 8rpx;
		/* 缩小内边距 */
		border-radius: 5rpx;
	}

	.grid-container {
		display: flex;
		justify-content: center;
		/* 水平居中 */
	}

	.grid-item {
		flex: 1 0 calc(50% - 10px);
		/* 每个网格项占50%宽度，并留出间距 */
		margin: 5rpx;
		/* 添加间距 */
	}

	.navigator-item {
		display: flex;
		flex-direction: column;
		/* 垂直排列 */
		align-items: center;
		/* 垂直居中 */
		justify-content: center;
		/* 水平居中 */
		height: 100%;
		/* 使整个项占满高度 */
		text-align: center;
		/* 文字居中对齐 */
	}

	.icon {
		margin-bottom: 3rpx;
		/* 图标与文字之间的间距 */
	}

	.text {
		color: #666;
		/* 更浅的颜色 */
		font-size: 14rpx;
		/* 缩小文字大小 */
	}

	.uni-section {
		margin-top: 20rpx;
		/* 增加部分之间的间距 */
	}
</style>