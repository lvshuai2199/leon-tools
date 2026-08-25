<template>
	<view class="content">
		<uni-search-bar @confirm="search" v-model="searchValue" @clear="clear" @focus="focus" @blur="blur" />
		<view class="search-result">
			<text class="search-result-text">当前输入为：{{ searchValue }}</text>
			<button type="default" @click="showTempRegPage">临时码生成</button>
		</view>
		<uni-section v-if="regInfoList.length > 0" title="注册码" type="line">
			<uni-collapse accordion v-model="accordionVal" @change="change">
				<uni-card v-for="(regInfo, index) in regInfoList" :key="index">
					<text class="uni-body">
						<!-- 根据状态显示已通过或未通过 -->
						<text :class="['status-text']">
							注册码类型：{{ regInfo.regCodeType === 1 ? '焊接专机' : '码垛专机' }}
						</text>
						<br />
						<text class="role-name">申请人：{{ regInfo.applyName }}</text>
						<br />
						<text class="role-name">公司：{{ regInfo.company }}</text>
						<br />
						<text class="role-description">销售：{{ regInfo.salesName }}</text>
						<br />

						<!-- 根据状态显示已通过或未通过 -->
						<text :class="['status-text', regInfo.applyStatus === 1 ? 'approved' : 'not-approved']">
							状态：{{ regInfo.applyStatus === 1 ? '已通过' : '未通过' }}
						</text>

					</text>
					<view slot="actions" class="card-actions">
						<view class="card-actions-item" @click="viewDetails(regInfo)">
							<uni-icons type="more-filled" size="18" color="#999"></uni-icons>
							<text class="card-actions-item-text">查看详情</text>
						</view>
						<view class="card-actions-item" v-if="regInfo.applyStatus != 1 && userId == 1"
							@click="approveApplication(regInfo)">
							<uni-icons type="heart" size="18" color="#999"></uni-icons>
							<text class="card-actions-item-text">通过</text>
						</view>
					</view>
				</uni-card>
			</uni-collapse>
		</uni-section>
		<view v-else>
			<text class="no-data-text">未获取到注册信息</text>
		</view>
		<view v-if="loading" class="loading">加载中...</view>

		<uni-popup ref="applyInfo" type="dialog">
			<view class="popup-content">
				<view class="info-display" v-for="(detail, index) in detailInfo" :key="index">
					<text class="info-label">{{ detail.label }}</text>
					<text class="info-value">{{ detail.value }}</text>
				</view>
				<view class="button-group">
					<button class="btn-withdraw" @click="withdrawApplication(currentDetail)">发送通知</button>
					<button class="btn-close" @click="closeDetailsPopup">关闭</button>
				</view>
			</view>
		</uni-popup>
		
		
		<uni-popup ref="popup" type="dialog">
			<view class="popup-content">
				<text>生成临时码</text>
				<uni-forms :modelValue="regData" :rules="rules">
					<uni-forms-item label="类型:" name="registrationType">
						<picker @change="onRegistrationTypeChange" :value="selectedRegistrationType" :range="registrationTypes">
							<view class="uni-input">{{ registrationTypes[selectedRegistrationType] || '请选择注册码类型' }}</view>
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

				</uni-forms>
				<view class="button-group">
					<button class="btn-withdraw" @click="tempCodeGene()">生成</button>
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
				regInfoList: [],
				originalRegInfoList: [],
				searchValue: null,
				accordionVal: [],
				loading: false,
				currentDetail: {},
				detailInfo: [],
				regData:{
					regCode: '',
					regCodeType: null,
					oneMonthValid: 'OneMonth',
					longTimeValid: 'Forever',
				},
				registrationTypes: ['焊接专机', '码垛专机'], // 下拉框选项
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
					this.getAllRegInfo();
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
			async getAllRegInfo() {
				this.loading = true;
				try {
					const response = await this.$api.getAllRegInfo();
					if (response.status === 200) {
						if (response.data && response.data.records) {
							this.regInfoList = response.data.records;
							this.originalRegInfoList = response.data.records;
						}
					} else {
						this.showToast('获取失败', 'error');
					}
				} catch (error) {
					console.error('获取注册表信息失败:', error);
				} finally {
					this.loading = false;
				}
			},
			async search() {
				if (this.searchValue) {
					this.regInfoList = this.originalRegInfoList.filter((regInfo) =>
						regInfo.company.includes(this.searchValue) || regInfo.salesName.includes(this.searchValue)
					);
				} else {
					this.regInfoList = this.originalRegInfoList;
				}
			},
			clear() {
				this.searchValue = '';
				this.regInfoList = this.originalRegInfoList;
			},
			viewDetails(regInfo) {
				this.currentDetail = regInfo;
				this.populateDetailInfo();
				this.$refs.applyInfo.open();
			},
			closeDetailsPopup() {
				this.$refs.applyInfo.close();
			},
			withdrawApplication(regInfo) {
				console.log('撤回申请:', regInfo);
				this.closeDetailsPopup();
			},
			async approveApplication(regInfo) {
				console.log('通过申请:', regInfo);
				try {
					const response = await this.$api.getRegCode(regInfo);
					if (response.status === 200) {
						this.getAllRegInfo();
						this.showToast('已通过！', 'error');
					} else {
						this.showToast('通过失败！' + response.message, 'error');

					}
				} catch (error) {
					console.error('获取注册表信息失败:', error);
				}
			},
			populateDetailInfo() {
				this.detailInfo = [{
						label: '申请人',
						value: this.currentDetail.applyName
					},
					{
						label: '公司',
						value: this.currentDetail.company
					},
					{
						label: '销售',
						value: this.currentDetail.salesName
					},
					{
						label: '电话',
						value: this.currentDetail.applyPhone
					},
					{
						label: '注册码',
						value: this.currentDetail.regCode
					},
					{
						label: '备注',
						value: this.currentDetail.remarks
					},
					{
						label: '生成验证码',
						value: ''
					}, // Add any relevant information here
					{
						label: '单月有效',
						value: this.currentDetail.oneMonthValid
					},
					{
						label: '长期有效',
						value: this.currentDetail.longTimeValid
					},
				];
			},
			showToast(title, icon) {
				uni.showToast({
					title,
					icon
				});
			},
			change(value) {
				console.log('Accordion changed:', value);
			},
			showTempRegPage() {
				this.$refs.popup.open();
			},
			closePopup() {
				this.$refs.popup.close();
			},
			// 处理注册码类型变化
			onRegistrationTypeChange(event) {
				this.selectedRegistrationType = event.detail.value; // 更新选中的索引
				if (this.selectedRegistrationType === 0) {
					this.regData.regCodeType = 1; // 焊接专机对应的注册码
				} else if (this.selectedRegistrationType === 1) {
					this.regData.regCodeType = 2; // 码垛专机对应的注册码
				}
			},
			createRegCode(regInfo) {
				console.log('生成注册码:', regInfo);
				this.closeDetailsPopup();
			},
			async tempCodeGene() {
				console.log("临时码生成");
				// 检查注册码长度是否为6位
				if (this.regData.regCodeType == null) {
				    uni.showToast({
				        title: '请选择注册码类型',
				        icon: 'none' // 使用 'none' 表示错误提示
				    });
				    return; // 结束方法执行
				}
				
				// 检查注册码长度是否为6位
				if (this.regData.regCode.length !== 6) {
				    uni.showToast({
				        title: '注册码长度必须为6位',
				        icon: 'none' // 使用 'none' 表示错误提示
				    });
				    return; // 结束方法执行
				}
							
				this.loading = true;
				try {
					const response = await this.$api.genTempRegCode(this.regData);
					if (response.status === 200) {
						if (response.data) {
							this.regData = response.data;
						}
					} else {
						this.showToast('获取失败,' + response.message);
					}
				} catch (error) {
					console.error('获取注册表信息失败:', error);
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
		/* 页面内边距 */
	}

	.search-result {
		margin-top: 10px;
		/* 增加搜索结果的上间距 */
		text-align: center;
		/* 中心对齐 */
		color: #999;
	}

	.uni-card {
		margin-bottom: 10px;
		/* 每个卡片之间的间距 */
	}

	.role-name {
		font-weight: bold;
		/* 加粗申请人 */
		color: #333;
		/* 申请人颜色 */
	}

	.role-description {
		color: #666;
		/* 描述颜色 */
		margin-top: 5px;
		/* 上间距 */
	}

	.card-actions {
		display: flex;
		/* 使用Flexbox布局 */
		justify-content: space-between;
		/* 在两边对齐 */
		align-items: center;
		/* 垂直居中 */
		margin-top: 2%;
	}

	.card-actions-item {
		display: flex;
		/* 使用Flexbox布局 */
		align-items: center;
		/* 垂直居中 */
		cursor: pointer;
		/* 鼠标样式 */
	}

	.card-actions-item-text {
		margin-left: 5px;
		/* 文本和图标之间的间距 */
	}

	.loading {
		text-align: center;
		/* 中心对齐 */
		margin-top: 20px;
		/* 上间距 */
		color: #999;
		/* 颜色 */
		font-size: 16px;
		/* 字体大小 */
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

	.info-display {
		display: flex;
		/* 使用Flexbox布局 */
		justify-content: space-between;
		/* 对齐内容 */
		margin-bottom: 8px;
		/* 每条信息之间的间距 */
	}

	.info-label {
		font-weight: bold;
		/* 加粗标签 */
	}

	.info-value {
		color: #333;
		/* 值的颜色 */
	}

	.no-data-text {
		text-align: center;
		/* 中心对齐 */
		color: #999;
		/* 颜色 */
		font-size: 16px;
		/* 字体大小 */
		margin-top: 20px;
		/* 上间距 */
	}

	.button-group {
		margin-top: 20px;
		/* 上间距 */
	}

	.btn-withdraw,
	.btn-close {
		width: 100%;
		/* 按钮宽度 */
		padding: 10px;
		/* 按钮内边距 */
		margin-top: 10px;
		/* 上间距 */
		border: none;
		/* 去掉边框 */
		border-radius: 4px;
		/* 圆角 */
		cursor: pointer;
		/* 鼠标样式 */
	}

	.btn-withdraw {
		background-color: #FF3B30;
		/* 撤回按钮颜色 */
		color: white;
		/* 文字颜色 */
	}

	.btn-close {
		background-color: #007AFF;
		/* 关闭按钮颜色 */
		color: white;
		/* 文字颜色 */
	}

	.status-text {
		font-weight: bold;
		/* 加粗文本 */
		margin-top: 5px;
		/* 上间距 */
	}

	.approved {
		color: #4CAF50;
		/* 绿色，表示已通过 */
	}

	.not-approved {
		color: #F44336;
		/* 红色，表示未通过 */
	}
</style>