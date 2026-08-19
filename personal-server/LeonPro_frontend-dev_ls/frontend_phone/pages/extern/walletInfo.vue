<template>
	<view class="content">
		<!-- 信息列表 -->
		<uni-section title="钱包" type="line">
			<uni-grid :column="2">
				<uni-grid-item>
					<text class="text">余额：</text>

				</uni-grid-item>
				<uni-grid-item>
					<text class="text">收入：</text>
				</uni-grid-item>
			</uni-grid>

		</uni-section>

		<uni-section title="详情" type="line">
			<uni-grid :column="4" :highlight="true" @change="change">
				<uni-grid-item v-for="(item, index) in accountInfoList" :index="index" :key="index">
					<view class="grid-item-box" style="background-color: #fff;">
						<uni-icons type="vip" :size="30" color="#777" /><br />
						<text class="text">{{item.accountName}}</text><br />
						<text class="text">{{item.accountBalance}}</text><br />
						<text class="text">{{item.accountBalance}}</text><br />
					</view>
				</uni-grid-item>
			</uni-grid>
			<!-- 增加储蓄卡消费 -->
			<!-- <button>回退上一条</button> -->
			<button @click="showTempRegPage">增加消费记录</button>
			<navigator url="/pages/extern/walletAdd" hover-class="navigator-hover" class="navigate-button">
				<uni-icons type="list" size="1"></uni-icons>
				<text class="text">查看详情</text>
			</navigator>
		</uni-section>

		<uni-section title="点击查看对应数据" type="line">
			<uni-card :title="wallet.walletName" :extra="messageTypes[wallet.walletType]"
				v-for="(wallet, index) in accountInfoList">
				<text class="uni-body">账户余额：{{wallet.walletAccount}}。</text><br />
				<text class="uni-body">使用次数：{{wallet.walletAccount}}。</text>
				<view v-if="wallet.walletType == 0">
					<button class="mini-btn">增加</button>
				</view>
				<view v-else slot="actions" class="card-actions">
					<view class="card-actions-item" @click="actionsClick('分享')">
						<uni-icons type="heart" size="18" color="#999"></uni-icons>
						<text class="card-actions-item-text">分享</text>
					</view>
					<view class="card-actions-item" @click="actionsClick('点赞')">
						<uni-icons type="heart" size="18" color="#999"></uni-icons>
						<text class="card-actions-item-text">账户细则</text>
					</view>
					<view class="card-actions-item" @click="checkWalletDetail(wallet.id)">
						<uni-icons type="chatbubble" size="18" color="#999"></uni-icons>
						<text class="card-actions-item-text">查看详情</text>
					</view>
				</view>
			</uni-card>
		</uni-section>


		<uni-popup ref="popup" type="dialog" background-color="#cdcdff">
			<view class="popup-content">
				<text>人员</text>
				<!-- 				<uni-forms :modelValue="regData" :rules="rules">
					<uni-forms-item label="类型:" name="registrationType">
						<picker @change="onRegistrationTypeChange" :value="selectedRegistrationType"
							:range="registrationTypes">
							<view class="uni-input">{{ registrationTypes[selectedRegistrationType] || '请选择注册码类型' }}
							</view>
						</picker>
					</uni-forms-item>

					<uni-forms-item label="单月:" name="applyName">
						<text>{{regData.oneMonthValid}}</text>
					</uni-forms-item>
					<uni-forms-item label="永久：" name="applyName">
						<text>{{regData.longTimeValid}}</text>
					</uni-forms-item>
					<uni-forms-item label="注册码:" name="applyName">
						<uni-easyinput v-model="regData.regCode" placeholder="输入注册码" />
					</uni-forms-item>

				</uni-forms> -->
				<uni-forms ref="form" :modelValue="formData" :rules="rules">
					<uni-forms-item label="姓名" name="name">
						<uni-easyinput type="text" v-model="formData.name" placeholder="请输入姓名" />
					</uni-forms-item>
					<uni-forms-item label="邮箱" name="email">
						<input class="input" v-model="formData.email" type="text" placeholder="请输入邮箱"
							@input="binddata('email',$event.detail.value)" />
					</uni-forms-item>
					<uni-forms-item label="参与人员" required>
						<uni-data-checkbox v-model="messageTypes" multiple :localdata="places" />
					</uni-forms-item>
					<uni-forms-item label="地点" required>
					</uni-forms-item>
				</uni-forms>
				<view class="button-group">
					<button class="btn-withdraw" @click="tempCodeGene()">回退该记录</button>
					<button class="btn-close" @click="closePopup">关闭</button>
				</view>
			</view>


		</uni-popup>
	</view>
</template>

<script>
	export default {
		data() {
			return {
				userId: '',
				accountInfoList: [], // 存储用户信息列表
				searchValue: null,
				pageInfo: {
					userId: '',
				},

				messageTypes: [1, 2],

				// 多选数据源
				places: [{
					text: '跑步',
					value: 0
				}, {
					text: '游泳',
					value: 1
				}, {
					text: '绘画',
					value: 2
				}, {
					text: '足球',
					value: 3
				}, {
					text: '篮球',
					value: 4
				}, {
					text: '其他',
					value: 5
				}],
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
					const response = await this.$api.getWalletAccountList();
					console.log('结果为', response);

					// 提取用户信息
					if (response.data && response.data.records && response.data.records.length > 0) {
						this.accountInfoList = response.data.records; // 保存用户信息列表
						console.log(this.accountInfoList)
					}
				} catch (error) {
					console.error('获取信息列表失败:', error);
				}
			},
			async checkWalletDetail(param) {
				const response = await this.$api.getWalletAccountList();
				this.$refs.popup.open();
			},
			showTempRegPage() {
				this.$refs.popup.open();
			},
			closePopup() {
				this.$refs.popup.close();
			},
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

	.popup-content {
		padding: 20px;
		/* 增加弹窗内边距 */
		background-color: white;
		/* 背景颜色 */
		width: 90%;
		/* 弹窗宽度 */
		max-width: 600px;
		/* 最大宽度 */
		border-radius: 8px;
		/* 圆角 */
	}
</style>