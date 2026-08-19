<template>
	<view class="content">
		<!-- 用户管理 -->
		<!-- TODO app 端默认不使用动画，app在使用高度动画的时候会有性能开销问题，所以应该要酌情使用 -->
		<uni-section v-if="userInfoList.length > 0" title="用户信息" type="line">
			<uni-search-bar @confirm="search" :focus="true" v-model="searchValue" @blur="blur" @focus="focus" @input="input"
				@cancel="cancel" @clear="clear">
			</uni-search-bar>
			<view class="search-result">
				<text class="search-result-text">当前输入为：{{ searchValue }}</text>
			</view>
			<uni-collapse  accordion v-model="accordionVal" @change="change">
				
				<uni-list-chat  v-for="(user, index) in userInfoList" :avatar-circle="true" :title="user.username" :avatar="user.avatarUrl" :note="user.roleId" :time="user.createTime" >
					
					<button>查看详情</button>
				</uni-list-chat>
<!-- 				<uni-collapse-item v-for="(user, index) in userInfoList" :key="index" :title="'用户 ' + (index + 1)" :show-animation="true">
					
					
					<view class="content">
						<text class="text">手风琴效果同时只会保留一个组件的打开状态，其余组件会自动关闭。</text>
						<view class="button-sp-area">
							<button class="mini-btn" type="primary" size="mini">更新</button>
							<button class="mini-btn" type="default" size="mini">禁用</button>
							<button class="mini-btn" type="warn" size="mini">重置密码</button>
						</view>
					</view>
				</uni-collapse-item> -->
			</uni-collapse>
		</uni-section>
		<view v-else>
		  <text>未获取到用户信息</text>
		</view>
	</view>
</template>

<script>
	export default {
		data() {
			return {
				userInfoList: [], // 存储用户信息列表
				searchValue:null
			}
		},
		onLoad() {
			this.wxGetUsers();
		},
		methods: {
			/***********用户管理的主要功能*******************/
			async wxGetUsers() {
			  console.error('开始获取用户信息');
			  try {
			    const response = await this.$api.getUserInfo();
			    console.log('结果为', response);
			
			    // 提取用户信息
			    if (response.data && response.data.records && response.data.records.length > 0) {
			      this.userInfoList = response.data.records; // 保存用户信息列表
			      console.log(this.userInfoList)
			    }
			  } catch (error) {
			    console.error('获取用户信息失败:', error);
			  }
			}
		}
	}
</script>

<style>
	.button-sp-area {
	  display: flex;
	  justify-content: space-between; /* 均匀分布按钮 */
	  align-items: center; /* 垂直居中按钮 */
	  padding: 10px; /* 可选：给容器添加内边距 */
	}
	
	.mini-btn {
	  flex: 1; /* 使按钮占据相同的宽度 */
	  margin: 0 5px; /* 可选：按钮之间的间距 */
	}
</style>

