<template>
	<view class="content">
		<!-- 信息列表 -->
		<uni-section title="零钱卡包" type="line">
			<uni-card :title="wallet.walletName" :extra="messageTypes[wallet.walletType]"
				v-for="(wallet, index) in walletInfoList">
				<text class="uni-body">账户余额：{{wallet.walletAccount}}。</text><br />
				<text class="uni-body">使用次数：{{wallet.walletAccount}}。</text>
				<view v-if="wallet.walletType == 0">
					<button class="mini-btn">增加</button>
				</view>
				<view v-else slot="actions" class="card-actions">
					<!-- 跳转到任务清单的按钮 -->
					<navigator url="/pages/extern/walletInfo" hover-class="navigator-hover" class="navigate-button">
						<uni-icons type="list" size="1"></uni-icons>
						<text class="text">查看详情</text>
					</navigator>
				</view>
			</uni-card>
		</uni-section>
	</view>
</template>

<script>
	export default {
		data() {
			return {
				userId: '',
				walletInfoList: [], // 存储用户信息列表
				searchValue: null,
				pageInfo: {
					userId: '',
				},
				messageTypes: ['个人钱包', '共享钱包'],
			}
		},
		onLoad() {
			// 获取用户 ID
			uni.getStorage({
				key: 'userId',
				success: (res) => { // 使用箭头函数
					console.log('用户 ID:', res.data);
					this.userId = res.data;
					console.log("set userId " + this.userId);
					this.getAllInfo();
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
			/***********用户管理的主要功能*******************/
			async getAllInfo() {
				console.error('开始获取信息列表');
				try {
					this.pageInfo.userId = this.userId;
					const response = await this.$api.getWalletList(this.pageInfo);
					console.log('结果为', response);

					// 提取用户信息
					if (response.data && response.data.records && response.data.records.length > 0) {
						this.walletInfoList = response.data.records; // 保存用户信息列表
						console.log(this.walletInfoList)
					}
				} catch (error) {
					console.error('获取信息列表失败:', error);
				}
			}
		}
	}
</script>

<style>
	.button-sp-area {
		display: flex;
		justify-content: space-between;
		/* 均匀分布按钮 */
		align-items: center;
		/* 垂直居中按钮 */
		padding: 10px;
		/* 可选：给容器添加内边距 */
	}

	.mini-btn {
		flex: 1;
		/* 使按钮占据相同的宽度 */
		margin: 0 5px;
		/* 可选：按钮之间的间距 */
	}
</style>