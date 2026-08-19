<template>
	<view class="uni-content" style="margin-bottom: 60px;">
		<!-- 搜索框 -->
		<uni-search-bar @confirm="getAllTasks" @cancel="resetSearch" v-model="pageInfo.searchValue"
			cancelButton="auto" />

		<view class="content-area">
			<uni-collapse accordion>
				<uni-collapse-item titleBorder="none" v-for="(item, idx) in tableData" :key="idx"
					:disabled="item.isDelete == 1">
					<template v-slot:title>
						<uni-list>
							<view class="task-info">
								<text class="task-index">{{ (pageInfo.current - 1) * pageInfo.size + idx + 1 }}.</text>
								<view class="task-details">
									<text class="uni-body">任务名称：{{ item.taskName }}<br /></text>
									<text class="uni-body">任务类型：{{ item.taskType }}<br /></text>
									<!-- 根据任务状态动态应用不同的样式 -->
									<text :class="getStatusClass(item.taskStatus)" class="task-status">
										任务状态：{{ statusRange[item.taskStatus].text }}<br />
									</text>
									<text class="uni-body">优先级：{{ item.taskLevel }}<br /></text>
									<text class="uni-body">创建时间：{{ item.createTime }}<br /></text>
								</view>
								<button type="default" v-if="item.isDelete == 1"
									@click="restoreTask(item.id)">恢复</button>
							</view>
						</uni-list>
					</template>

					<view class="content">
						<uni-card title="客户信息" padding>
							<view class="customer-info">
								<text class="uni-body">客户：{{ item.customerName }}</text>
								<text class="uni-body">地点：{{ item.customerPlace }}</text>
								<text class="uni-body">行业：{{ item.industry }}</text>
								<text class="uni-body">场景：{{ item.scenario }}</text>
								<text class="uni-body">机械臂类型：{{ item.robotType }}</text>
								<text class="uni-body">机械臂数量：{{ item.robotNum }}</text>
								<text class="uni-body">发布人：{{ item.publisherId }}</text>
							</view>
						</uni-card>

						<uni-card title="详细信息" :extra="'联系人：' + item.publisherId" padding>
							<text class="uni-body">{{ item.description }}</text>
						</uni-card>

						<uni-card title="详情" type="line" padding>
							<uni-easyinput type="textarea" autoHeight v-model="item.remarks"
								placeholder="备注信息"></uni-easyinput>
						</uni-card>

						<uni-section title="操作" type="line" padding>
							<view class="button-group">
								<navigator
									:url="'/pages/task/taskEdit?item=' + encodeURIComponent(JSON.stringify(item.id))">
									<button type="default">修改</button>
								</navigator>
								<button v-if="item.taskStatus !== statusRange.length - 1" type="primary" size="mini"
									@click="updateTaskStateInfo(idx, Number(item.taskStatus) + 1)">下一步</button>
							</view>
						</uni-section>
					</view>
				</uni-collapse-item>
			</uni-collapse>
		</view>

		<!-- 悬浮导航栏 -->
		<view class="bottom-nav">
			<navigator url="/pages/task/taskEdit" open-type="navigate" hover-class="other-navigator-hover">
				<button type="default">新增</button>
			</navigator>
			<button @click="viewStatistics" class="nav-button">统计</button>
			<button @click="openSettings" class="nav-button">设置</button>
			<button @click="previousPage" class="nav-button">
				<uni-icons type="arrow-left"></uni-icons>
			</button>
			<text>{{pageInfo.current}}/{{pageInfo.pages}}</text>
			<button @click="nextPage" class="nav-button">
				<uni-icons type="arrow-right"></uni-icons>
			</button>
		</view>

		<!-- 提示信息弹窗 -->
		<uni-popup ref="message" type="message">
			<uni-popup-message :type="msgType" :message="messageText" :duration="2000"></uni-popup-message>
		</uni-popup>

		<!-- 设置弹窗 -->
		<uni-popup ref="settingsModal" type="dialog">
			<view class="modal-content">
				<text class="modal-title">设置</text>

				<uni-forms ref="settingsForm" :model="pageInfo" :rules="rules">
					<!-- 每页显示数量 -->
					<uni-forms-item label="显示数量" prop="size">
						<uni-easyinput v-model="pageInfo.size" placeholder="请输入每页显示数量" type="number"></uni-easyinput>
					</uni-forms-item>

					<!-- 任务名称 -->
					<uni-forms-item label="任务名称" prop="taskName">
						<uni-easyinput v-model="pageInfo.taskName" placeholder="请输入任务名称"></uni-easyinput>
					</uni-forms-item>

					<!-- 任务等级 -->
					<uni-forms-item label="任务等级" prop="taskLevel">
						<uni-easyinput v-model="pageInfo.taskLevel" placeholder="请输入任务等级"></uni-easyinput>
					</uni-forms-item>

					<!-- 任务状态 -->
					<uni-forms-item label="任务状态" prop="taskStatus">
						<uni-data-select v-model="pageInfo.taskStatus" :localdata="statusRange"
							:clear="false"></uni-data-select>
							<text>{{pageInfo.taskStatus}}</text>
					</uni-forms-item>

					<!-- 任务类别 -->
					<uni-forms-item label="任务类别" prop="taskType">
						<uni-easyinput v-model="pageInfo.taskType" placeholder="请输入任务类别"></uni-easyinput>
					</uni-forms-item>

					<!-- 是否删除 -->
					<uni-forms-item label="是否删除" prop="isDelete">
						<uni-data-select v-model="pageInfo.isDelete" :localdata="delRange"
							:clear="false"></uni-data-select>
					</uni-forms-item>

					<!-- 开始时间 -->
					<uni-forms-item label="开始时间" prop="startTime">
						<view class="uni-list-cell-db">
							<picker mode="date" :value="pageInfo.startTime || ''" @change="onStartDateChange"
								@clear="clearStartDate">
								<view class="uni-input">{{ pageInfo.startTime ? pageInfo.startTime : '无日期' }}</view>
							</picker>
						</view>
					</uni-forms-item>

					<!-- 结束时间 -->
					<uni-forms-item label="结束时间" prop="endTime">
						<view class="uni-list-cell-db">
							<picker mode="date" :value="pageInfo.endTime || ''" @change="onEndDateChange"
								@clear="clearEndDate">
								<view class="uni-input">{{ pageInfo.endTime ? pageInfo.endTime : '无日期' }}</view>
							</picker>
						</view>
					</uni-forms-item>



				</uni-forms>

				<!-- 按钮 -->
				<view class="modal-buttons">
					<uni-button type="default" @click="closeSettingsModal">取消</uni-button>
					<uni-button type="primary" @click="applySettings">应用</uni-button>
				</view>
			</view>
		</uni-popup>

	</view>
</template>

<script>
	export default {
		data() {
			return {
				pageInfo: {
					searchValue: '', // 搜索框的值
					current: 1, // 当前页数
					pages: 0, // 总页数
					size: 10, // 每页显示数量
					total: 0, // 数据总量
					isDelete: 0, // 任务是否删除
					taskName: '', // 任务名称
					taskLevel: '', // 任务等级
					taskStatus: '', // 任务状态
					customerName: '', // 客户名称
					customerPlace: '', // 客户地址
					taskType: '', // 任务类别
					industry: '', // 所属行业
					scenario: '', // 场景
					startTime: null, // 开始时间，初始化为 null
					endTime: null, // 结束时间，初始化为 null
					publisherId: ''
				},
				tableData: [],
				msgType: null,
				messageText: null,
				isEditing: false,
				statusRange: [
				    { value: '', text: '无' },      // status 0
				    { value: 1, text: '待处理' },  // status 1
				    { value: 2, text: '处理中' },    // status 2
				    { value: 3, text: '已完成' },    // status 3
				    { value: 4, text: '异常' },      // status 4
				],
				delRange: [{
						value: 0,
						text: "否"
					},
					{
						value: 1,
						text: "是"
					},
				],
			};
		},
		onShow() {
			// 获取用户 ID
			uni.getStorage({
				key: 'userId',
				success: (res) => { // 使用箭头函数
					console.log('用户 ID:', res.data);
					this.pageInfo.publisherId = res.data; // 正确设置 publisherId
					console.log("set publisherId " + this.pageInfo.publisherId);
				},
				fail: () => {
					console.log('获取用户 ID 失败');
				}
			});
			console.log("开始检索数据");
			this.getAllTasks();
		},

		methods: {
			resetSearch() {
				this.pageInfo.searchValue = '';
				this.getAllTasks();
			},
			async getAllTasks() {
				try {
					this.tableData = [];
					const response = await this.$api.getAllTasks({
						...this.pageInfo
					});
					if (response.status === 200) {
						this.tableData = response.data.records || [];
						Object.assign(this.pageInfo, {
							current: response.data.current,
							pages: response.data.pages,
							size: response.data.size,
							total: response.data.total,
						});
					} else {
						this.messageToggle('error', '任务列表获取失败！');
					}
				} catch (error) {
					this.messageToggle('error', '请求失败，请稍后再试！');
				}
			},
			messageToggle(type, content) {
				this.msgType = type;
				this.messageText = content;
				this.$refs.message.open();
			},
			getStatusClass(taskStatus) {
				switch (taskStatus) {
					case '1':
						return 'status-normal'; // 正常状态
					case '2':
						return 'status-completed'; // 已完成状态
					case '3':
						return 'status-completed'; // 已完成状态
					case '4':
						return 'status-error'; // 异常状态
					default:
						return 'status-normal'; // 默认状态
				}
			},
			async updateTaskStateInfo(curIndex, stateNum) {
				this.tableData[curIndex].taskStatus = Number(stateNum);
				const taskInfo = {
					...this.tableData[curIndex]
				};
				const response = await this.$api.updateTask(taskInfo);
				if (response.status === 200) {
					this.messageToggle('success', '状态更新成功');
					this.pageInfo.isDelete = 0;
					this.getAllTasks();
				} else {
					this.messageToggle('error', '状态更新失败');
				}
			},
			async restoreTask(restoreID) {
				const restoreDel = 0;
				const response = await this.$api.updateTask({
					id: restoreID,
					isDelete: restoreDel,
				});

				if (response.status === 200) {
					this.messageToggle('success', '任务恢复成功');
					this.pageInfo.isDelete = 0;
					this.getAllTasks();
				} else {
					this.messageToggle('error', '任务恢复失败');
				}
			},
			openSettings() {
				this.$refs.settingsModal.open(); // 打开设置弹窗
			},
			closeSettingsModal() {
				this.$refs.settingsModal.close(); // 关闭设置弹窗
			},
			applySettings() {
				this.getAllTasks(); // 重新获取任务列表
				this.closeSettingsModal(); // 关闭设置弹窗
			},
			previousPage() {
				if (this.pageInfo.current > 1) {
					this.pageInfo.current -= 1; // 减少当前页数
					this.getAllTasks(); // 重新获取任务
				} else {
					this.messageToggle('error', '当前为第一页！');
				}
			},
			nextPage() {
				if (this.pageInfo.current < this.pageInfo.pages) {
					this.pageInfo.current += 1; // 增加当前页数
					this.getAllTasks(); // 重新获取任务
				} else {
					this.messageToggle('error', '已是最后一页！');
				}
			},
			onStartDateChange(event) {
				this.pageInfo.startTime = event.detail.value;
				console.log("更新的开始时间:", this.pageInfo.startTime);
			},
			onEndDateChange(event) {
				this.pageInfo.endTime = event.detail.value;
				console.log("更新的结束时间:", this.pageInfo.endTime);
			},
			clearStartDate() {
				this.pageInfo.startTime = null; // 清除开始时间
				console.log("清除开始时间:", this.pageInfo.startTime);
			},
			clearEndDate() {
				this.pageInfo.endTime = null; // 清除结束时间
				console.log("清除结束时间:", this.pageInfo.endTime);
			}
		}
	};
</script>

<style scoped>
	.task-info {
		display: flex;
		align-items: center;
	}

	.task-index {
		margin-left: 2%;
		margin-right: 10px;
		font-weight: bold;
	}

	.task-details {
		flex-grow: 1;
	}

	.button-group {
		display: flex;
		justify-content: space-between;
		/* 平均分配按钮的空间 */
	}

	.button-group>* {
		flex: 1;
		/* 让所有子元素（按钮和导航）占据相同的空间 */
		margin: 0 20px;
		/* 添加按钮之间的间隔 */
	}

	.button-group button {
		min-width: 0;
		/* 防止按钮过宽溢出 */
	}

	.modal-content {
		padding: 20px;
	}

	.modal-title {
		font-size: 20px;
		font-weight: bold;
		margin-bottom: 15px;
	}

	.modal-buttons {
		display: flex;
		justify-content: space-between;
		margin-top: 20px;
	}

	.uni-body {
		padding: 4px;
		margin-bottom: 3px;
		line-height: 1.2;
	}

	.content {
		margin-bottom: 10px;
	}

	/* 悬浮导航栏样式 */
	.bottom-nav {
		display: flex;
		justify-content: space-around;
		align-items: center;
		height: 60px;
		background-color: #dfdeff;
		position: sticky;
		bottom: 0;
		left: 0;
		right: 0;
		z-index: 1000;
		box-shadow: 0 -2px 5px rgba(0, 0, 0, 0.3);
	}

	/* 内容区域样式 */
	.content-area {
		height: calc(100vh - 5px);
		overflow-y: auto;
	}

	.nav-button {
		color: #4f4f4f;
		background: none;
		border: none;
		cursor: pointer;
		font-size: 16px;
		transition: background-color 0.3s;
	}

	.nav-button:hover {
		background-color: rgba(255, 255, 255, 0.2);
	}

	.status-error {
		color: red;
		/* 任务状态为最后一个时，文本颜色为红色 */
	}

	.status-normal {
		background-color: #e0f7fa;
		/* 正常状态背景色 */
		color: #00796b;
		/* 正常状态文字颜色 */
		border-radius: 8px;
		/* 圆角 */
		padding: 2px;
		/* 内边距 */
	}

	.status-completed {
		background-color: #c8e6c9;
		/* 已完成状态背景色 */
		color: #388e3c;
		/* 已完成状态文字颜色 */
		border-radius: 8px;
		/* 圆角 */
		padding: 2px;
		/* 内边距 */
	}

	.status-error {
		background-color: #ffcdd2;
		/* 异常状态背景色 */
		color: #d32f2f;
		/* 异常状态文字颜色 */
		border-radius: 8px;
		/* 圆角 */
		padding: 2px;
		/* 内边距 */
	}

	.task-status {
		margin: 5px 0;
		/* 任务状态之间的间距 */
	}

	.customer-info {
		display: flex;
		flex-direction: column;
		/* 垂直排列 */
		margin: 10px 0;
		/* 上下间距 */
	}

	.uni-body {
		padding: 4px 0;
		/* 每个信息项的上下间隔 */
	}

	.uni-card {
		margin-bottom: 15px;
		/* 卡片之间的间距 */
	}

	.modal-content {
		padding: 20px;
		background-color: white;
	}

	.modal-title {
		font-size: 18px;
		font-weight: bold;
		margin-bottom: 10px;
	}

	.select-wrapper {
		border: 1px solid #ddd;
		/* 添加边框 */
		border-radius: 4px;
		/* 圆角 */
		margin-bottom: 10px;
		/* 底部间距 */
	}

	.uni-list-cell {
		border: 1px solid #ddd;
		/* 给日期选择器添加边框 */
		border-radius: 4px;
		/* 圆角 */
		margin-bottom: 10px;
		/* 底部间距 */
		padding: 10px;
		/* 内边距 */
		background-color: #f9f9f9;
		/* 背景色 */
	}

	.uni-list-cell-left {
		color: #333;
		/* 标签颜色 */
	}

	.uni-list-cell-db {
		color: #666;
		/* 数据颜色 */
	}

	.modal-buttons {
		display: flex;
		justify-content: space-between;
		margin-top: 20px;
	}

	.modal-content {
		padding: 20px;
		/* 弹窗内边距 */
	}

	.modal-title {
		font-size: 20px;
		font-weight: bold;
		margin-bottom: 15px;
	}

	.modal-buttons {
		display: flex;
		justify-content: space-between;
		margin-top: 20px;
	}

	.uni-title {
		margin-top: 15px;
		/* 日期选择器标题的上边距 */
	}
</style>