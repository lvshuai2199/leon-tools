<template>
	<view class="container">
		<view class="intro">My Home Page！--Leon</view>
		<text class="intro">详见：</text>
		<uni-link :href="href" :text="href"></uni-link>

		<!-- 每日任务模块 -->
		<view class="task-module">
			<text class="module-title">每日任务</text>

			<!-- 跳转到任务清单的按钮 -->
			<navigator url="/pages/task/task" hover-class="navigator-hover" class="navigate-button">
				<uni-icons type="list" size="1"></uni-icons>
				<text class="text">查看任务清单</text>
			</navigator>
			<!-- 跳转到任务清单的按钮 -->
			<navigator url="/pages/registration/regApply" hover-class="navigator-hover" class="navigate-button">
				<uni-icons type="list" size="1"></uni-icons>
				<text class="text">注册码申请</text>
			</navigator>
		</view>

		<view class="additional-info">
			<text>PDF查看</text>
			<text>文件上传</text>
			<text>快速沟通组</text>
		</view>
	</view>
</template>

<script>
	export default {
		data() {
			return {
				userId: '',
				href: 'https://uniapp.dcloud.io/component/README?id=uniui',
			}
		},
		// 在 pages/user/user.vue、pages/role/role.vue 等页面中
		onLoad() {
			// 获取用户 ID
			uni.getStorage({
				key: 'userId',
				success: (res) => { // 使用箭头函数
					console.log('用户 ID:', res.data);
					this.userInfo.id = res.data;
					console.log("set userId ");
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
	}
</script>

<style>
	.container {
		padding: 20px;
		font-size: 14px;
		line-height: 24px;
		background-color: #f0f0f0;
		/* 背景色 */
	}

	.intro {
		margin-bottom: 15px;
		/* 与下方内容的间距 */
		color: #333;
		font-weight: bold;
		/* 加粗 */
	}

	.task-module {
		margin-top: 20px;
		/* 与其他内容保持间隔 */
		padding: 20px;
		border: 1px solid #ccc;
		/* 边框 */
		border-radius: 10px;
		/* 圆角 */
		background-color: #fff;
		/* 背景色 */
		box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
		/* 增加阴影效果 */
	}

	.module-title {
		font-size: 18px;
		/* 模块标题的字体大小 */
		font-weight: bold;
		/* 加粗 */
		margin-bottom: 15px;
		/* 模块标题与内容的间距 */
		color: #007AFF;
		/* 标题颜色 */
	}

	.navigate-button {
		display: flex;
		/* 使用 flexbox 布局 */
		align-items: center;
		/* 垂直居中 */
		margin-top: 15px;
		/* 与任务列表的间隔 */
		padding: 10px;
		/* 按钮内边距 */
		background-color: #007AFF;
		/* 按钮背景色 */
		color: white;
		/* 按钮文本颜色 */
		border-radius: 5px;
		/* 圆角 */
		text-align: center;
		/* 文本居中 */
		text-decoration: none;
		/* 去掉下划线 */
		transition: background-color 0.3s;
		/* 增加过渡效果 */
	}

	.navigate-button:hover {
		background-color: #0056b3;
		/* 悬停时的背景色 */
	}

	.additional-info {
		margin-top: 20px;
		/* 与任务模块的间隔 */
		color: #333;
		/* 信息文字颜色 */
	}
</style>