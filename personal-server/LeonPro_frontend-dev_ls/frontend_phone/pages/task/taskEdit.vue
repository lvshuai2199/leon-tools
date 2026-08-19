<template>
	<view class="modal-container">
		<text class="modal-title">{{ isEditing ? '修改任务' : '新增任务' }}</text>

		<!-- 任务输入区域 -->
		<uni-forms ref="taskForm" :model="currentTask" :rules="rules">
			<uni-forms-item label="任务名称" prop="taskName">
				<uni-easyinput v-model="currentTask.taskName" placeholder="任务名称"></uni-easyinput>
			</uni-forms-item>
			<uni-forms-item label="任务类型" prop="taskType">
				<uni-easyinput v-model="currentTask.taskType" placeholder="任务类型"></uni-easyinput>
			</uni-forms-item>
			<uni-forms-item label="优先级" prop="taskType">
				<uni-easyinput v-model="currentTask.taskLevel" placeholder="优先级"></uni-easyinput>
			</uni-forms-item>
			<uni-forms-item label="客户名称" prop="customerName">
				<uni-easyinput v-model="currentTask.customerName" placeholder="客户名称"></uni-easyinput>
			</uni-forms-item>
			<uni-forms-item label="客户地点" prop="customerPlace">
				<uni-easyinput v-model="currentTask.customerPlace" placeholder="客户地点"></uni-easyinput>
			</uni-forms-item>
			<uni-forms-item label="行业" prop="industry">
				<uni-easyinput v-model="currentTask.industry" placeholder="行业"></uni-easyinput>
			</uni-forms-item>
			<uni-forms-item label="场景" prop="scenario">
				<uni-easyinput v-model="currentTask.scenario" placeholder="场景"></uni-easyinput>
			</uni-forms-item>
			<uni-forms-item label="机械臂类型" prop="robotType">
				<uni-easyinput v-model="currentTask.robotType" placeholder="机械臂类型"></uni-easyinput>
			</uni-forms-item>
			<uni-forms-item label="机械臂数量" prop="robotNum">
				<uni-easyinput v-model="currentTask.robotNum" placeholder="机械臂数量" type="number"></uni-easyinput>
			</uni-forms-item>
			<uni-forms-item label="任务描述" prop="description">
				<uni-easyinput type="textarea" v-model="currentTask.description" placeholder="任务描述"></uni-easyinput>
			</uni-forms-item>
			<uni-forms-item label="任务状态" prop="description">
				<text>{{statusRange[currentTask.taskStatus].text}}</text>
			</uni-forms-item>
			<uni-forms-item label="切换为:" prop="taskStatus">


				<uni-data-select v-model="currentTask.taskStatus" :localdata="statusRange"
					:clear="false"></uni-data-select>
			</uni-forms-item>

		</uni-forms>

		<!-- 编辑模式下的额外操作 -->
		<view class="modal-content" v-if="isEditing">
			<button class="mini-btn delete-btn" size="mini" @click="confirmDelete(currentTask.id)"
				type="warn">删除</button>
			<button class="mini-btn error-btn" size="mini" @click="openErrorModal(currentTask.id)"
				type="warn">异常</button>
		</view>

		<!-- 模态框按钮 -->
		<view class="modal-buttons">
			<uni-button type="default" @click="navigateBack">取消</uni-button>
			<uni-button type="primary" @click="submitTask"> {{ isEditing ? '保存' : '提交' }}</uni-button>
		</view>

		<!-- 提示信息弹窗 -->
		<uni-popup ref="message" type="message">
			<uni-popup-message :type="msgType" :message="messageText" :duration="2000"></uni-popup-message>
		</uni-popup>

		<!-- 异常备注输入弹窗 -->
		<uni-popup ref="errorModal" type="dialog">
			<view class="modal-content">
				<text class="modal-title">输入异常信息</text>
				<uni-easyinput type="textarea" v-model="errorRemark" placeholder="请输入异常信息"></uni-easyinput>
				<view class="modal-buttons">
					<uni-button type="default" @click="closeErrorModal">取消</uni-button>
					<uni-button type="primary" @click="submitErrorRemark">提交</uni-button>
				</view>
			</view>
		</uni-popup>

		<!-- 删除确认弹窗 -->
		<uni-popup ref="confirmDeleteModal" type="dialog">
			<view class="modal-content">
				<text class="modal-title">确认删除</text>
				<text>您确定要删除此任务吗？</text>
				<view class="modal-buttons">
					<uni-button type="default" @click="closeConfirmDeleteModal">取消</uni-button>
					<uni-button type="primary" @click="deleteTask">确认</uni-button>
				</view>
			</view>
		</uni-popup>
	</view>
</template>

<script>
	export default {
		data() {
			return {
				currentTask: {
					id: null,
					taskName: '',
					taskType: '',
					customerName: '',
					customerPlace: '',
					industry: '',
					scenario: '',
					robotType: '',
					robotNum: '',
					description: '',
					taskStatus: '' // 默认状态设为待处理
				},
				isEditing: false,
				errorRemark: '',
				statusRange: [{
						value: '',
						text: '无'
					}, // status 0
					{
						value: 1,
						text: '待处理'
					}, // status 1
					{
						value: 2,
						text: '处理中'
					}, // status 2
					{
						value: 3,
						text: '已完成'
					}, // status 3
					{
						value: 4,
						text: '异常'
					}, // status 4
				],
				msgType: null,
				messageText: null,
				rules: {
					taskName: {
						required: true,
						message: '任务名称不能为空'
					},
					taskType: {
						required: true,
						message: '任务类型不能为空'
					},
					// 可以在这里添加更多验证规则
				}
			};
		},
		onLoad(option) {
			if (option && option.item) {
				try {
					const item = JSON.parse(decodeURIComponent(option.item));
					this.isEditing = true;
					this.currentTask.id = item;
					this.getTaskInfo();
					this.currentTask.taskStatus = 3;
				} catch (error) {
					console.error("解析传入数据时出错:", error);
				}
			} else {
				// 获取用户 ID
				uni.getStorage({
					key: 'userId',
					success: (res) => {
						console.log('用户 ID:', res.data);
						this.currentTask.publisherId = res.data;
					},
					fail: () => {
						console.log('获取用户 ID 失败');
					}
				});
			};

		},
		methods: {
			messageToggle(type, content) {
				this.msgType = type;
				this.messageText = content;
				this.$refs.message.open();
			},
			async getTaskInfo() {
				if (!this.currentTask.id) {
					this.messageToggle('error', '任务ID未定义！');
					return;
				}

				try {
					const response = await this.$api.getAllTasks({
						id: this.currentTask.id
					});
					if (response.status === 200 && response.data.records.length > 0) {
						// 确保获取到的数据项
						this.currentTask = response.data.records[0];
					} else {
						this.messageToggle('error', '任务列表获取失败！');
					}
				} catch (error) {
					this.messageToggle('error', '请求失败，请稍后再试！');
				}
			},

			async submitTask() {
				// 使用 uni-forms 进行验证
				const valid = await this.$refs.taskForm.validate();
				if (!valid) {
					return; // 如果验证失败，直接返回
				}

				try {
					const response = this.isEditing ?
						await this.$api.updateTask(this.currentTask) :
						await this.$api.addTask(this.currentTask);

					const message = this.isEditing ? '任务修改成功！' : '任务新增成功！';
					if (response.status === 200) {
						this.messageToggle('success', message);
						uni.navigateBack();
					} else {
						this.messageToggle('error', `${message}失败！`);
					}
				} catch (error) {
					this.messageToggle('error', '请求失败，请稍后再试！');
				}
			},
			navigateBack() {
				uni.navigateBack();
			},
			confirmDelete(taskId) {
				this.currentTask.id = taskId;
				this.$refs.confirmDeleteModal.open();
			},
			closeConfirmDeleteModal() {
				this.$refs.confirmDeleteModal.close();
			},
			async deleteTask() {
				this.currentTask.isDelete = 1;
				const response = await this.$api.updateTask({
					id: this.currentTask.id,
					isDelete: this.currentTask.isDelete,
				});
				if (response.status === 200) {
					this.messageToggle('success', '任务删除成功');
					this.closeConfirmDeleteModal();
					uni.navigateBack();
				} else {
					this.messageToggle('error', '任务删除失败');
				}
			},
			openErrorModal(taskId) {
				this.currentTask.id = taskId;
				this.errorRemark = '';
				this.$refs.errorModal.open();
			},
			closeErrorModal() {
				this.$refs.errorModal.close();
			},
			async submitErrorRemark() {
				if (!this.errorRemark) {
					this.messageToggle('error', '请填写异常信息');
					return;
				}

				const response = await this.$api.submitErrorRemark({
					id: this.currentTask.id,
					remarks: this.errorRemark
				});

				if (response.status === 200) {
					this.messageToggle('success', '异常信息提交成功');
				} else {
					this.messageToggle('error', '异常信息提交失败');
				}
				this.closeErrorModal();
			}
		}
	}
</script>

<style scoped>
	.modal-container {
		padding: 20px;
		background-color: #fff;
		border-radius: 8px;
		box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
	}

	.modal-title {
		font-size: 20px;
		font-weight: bold;
		margin-bottom: 15px;
		color: #333;
	}

	.input-group {
		margin-bottom: 20px;
	}

	.modal-buttons {
		display: flex;
		justify-content: space-between;
	}

	.mini-btn {
		width: 48%;
	}

	.delete-btn {
		background-color: #ff4d4f;
		color: white;
	}

	.error-btn {
		background-color: #ffec3d;
		color: #333;
	}
</style>