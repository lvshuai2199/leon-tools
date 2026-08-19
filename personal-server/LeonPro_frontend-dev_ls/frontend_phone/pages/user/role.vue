<template>
  <view class="content">
    <!-- 用户管理 -->
    <uni-section v-if="roleList.length > 0" title="权限目录" type="line">
      <uni-search-bar @confirm="search" :focus="true" v-model="searchValue" @blur="blur" @focus="focus" @input="input"
        @cancel="cancel" @clear="clear">
      </uni-search-bar>
      <view class="search-result">
        <text class="search-result-text">当前输入为：{{ searchValue }}</text>
      </view>
      <view class="role-list">
        <view class="role-item" v-for="(role, index) in roleList" :key="index">
          <text class="role-name">{{ role.roleName }}</text>
          <text class="role-description">{{ role.description }}</text>
          <view class="button-sp-area">
            <button class="mini-btn" type="primary" size="mini" plain="true" @click="openUpdateRoleModal(role)">更新</button>
            <button class="mini-btn" v-if="role.isDisabled == 0" type="default" size="mini" plain="true" @click="disableRole(role)">禁用</button>
            <button class="mini-btn" v-else type="default" size="mini" plain="true" @click="enableRole(role)">启用</button>
            <button class="mini-btn" type="primary" size="mini" plain="true" @click="enableRole(role)">菜单配置</button>
		  </view>
        </view>
      </view>
    </uni-section>
    <view v-else>
      <text>未获取到用户信息</text>
    </view>

    <!-- 新增角色按钮 -->
    <button class="add-role-btn" @click="openAddRoleModal">新增角色</button>

    <!-- 新增角色模态框 -->
    <uni-popup ref="addRoleModal" type="dialog">
      <view class="modal-content">
        <text class="modal-title">新增角色</text>
        <uni-easyinput v-model="newRole.roleName" placeholder="角色名称" class="input-field"></uni-easyinput>
        <uni-easyinput v-model="newRole.description" placeholder="角色描述" class="input-field"></uni-easyinput>
        <view class="modal-buttons">
          <uni-button type="default" @click="closeAddRoleModal">取消</uni-button>
          <uni-button type="primary" @click="submitNewRole">提交</uni-button>
        </view>
      </view>
    </uni-popup>

    <!-- 更新角色模态框 -->
    <uni-popup ref="updateRoleModal" type="dialog">
      <view class="modal-content">
        <text class="modal-title">更新角色</text>
        <uni-easyinput v-model="currentRole.roleName" placeholder="角色名称" class="input-field"></uni-easyinput>
        <uni-easyinput v-model="currentRole.description" placeholder="角色描述" class="input-field"></uni-easyinput>
        <view class="modal-buttons">
          <uni-button type="default" @click="closeUpdateRoleModal">取消</uni-button>
          <uni-button type="primary" @click="submitUpdatedRole">保存</uni-button>
        </view>
      </view>
    </uni-popup>
  </view>
</template>

<script>
export default {
  data() {
    return {
      roleList: [], // 存储用户信息列表
      searchValue: null,
      newRole: {
        roleName: '',
        description: ''
      },
      currentRole: { // 用于更新角色的临时数据
        roleName: '',
        description: ''
      }
    }
  },
  onLoad() {
    this.getAllRole();
  },
  methods: {
    // 用户管理的主要功能
    async getAllRole() {
      console.error('开始获取用户信息');
      try {
        const response = await this.$api.getAllRole();
        console.log('结果为', response);
        if (response.data && response.data.records && response.data.records.length > 0) {
          this.roleList = response.data.records; // 保存用户信息列表
          console.log(this.roleList);
        }
      } catch (error) {
        console.error('获取用户信息失败:', error);
      }
    },
    openAddRoleModal() {
      this.newRole = { roleName: '', description: '' }; // 重置新增角色数据
      this.$refs.addRoleModal.open(); // 打开模态框
    },
    closeAddRoleModal() {
      this.$refs.addRoleModal.close(); // 关闭模态框
    },
    async submitNewRole() {
      try {
        const response = await this.$api.addRole(this.newRole); // 调用 API 添加角色
        if (response.status === 200) {
          this.closeAddRoleModal();
          this.getAllRole(); // 刷新角色列表
          uni.showToast({ title: '角色新增成功', icon: 'success' });
        } else {
          uni.showToast({ title: '角色新增失败', icon: 'none' });
        }
      } catch (error) {
        console.error('新增角色失败:', error);
        uni.showToast({ title: '请求失败，请稍后再试', icon: 'none' });
      }
    },
    async disableRole(role) {
      try {
        this.currentRole = { ...role };
        this.currentRole.isDisabled = 1; // 禁用角色
        const response = await this.$api.updateRole(this.currentRole); // 调用 API 更新角色
        if (response.status === 200) {
          this.getAllRole(); // 刷新角色列表
          uni.showToast({ title: '角色禁用成功', icon: 'success' });
        } else {
          uni.showToast({ title: '角色禁用失败', icon: 'none' });
        }
      } catch (error) {
        console.error('禁用角色失败:', error);
        uni.showToast({ title: '请求失败，请稍后再试', icon: 'none' });
      }
    },
    async enableRole(role) {
      try {
        this.currentRole = { ...role };
        this.currentRole.isDisabled = 0; // 启用角色
        const response = await this.$api.updateRole(this.currentRole); // 调用 API 更新角色
        if (response.status === 200) {
          this.getAllRole(); // 刷新角色列表
          uni.showToast({ title: '角色启用成功', icon: 'success' });
        } else {
          uni.showToast({ title: '角色启用失败', icon: 'none' });
        }
      } catch (error) {
        console.error('启用角色失败:', error);
        uni.showToast({ title: '请求失败，请稍后再试', icon: 'none' });
      }
    },
    openUpdateRoleModal(role) {
      this.currentRole = { ...role }; // 深拷贝用户信息以进行更新
      this.$refs.updateRoleModal.open(); // 打开更新模态框
    },
    closeUpdateRoleModal() {
      this.$refs.updateRoleModal.close(); // 关闭更新模态框
    },
    async submitUpdatedRole() {
      try {
        const response = await this.$api.updateRole(this.currentRole); // 调用 API 更新角色
        if (response.status === 200) {
          this.closeUpdateRoleModal();
          this.getAllRole(); // 刷新角色列表
          uni.showToast({ title: '角色更新成功', icon: 'success' });
        } else {
          uni.showToast({ title: '角色更新失败', icon: 'none' });
        }
      } catch (error) {
        console.error('更新角色失败:', error);
        uni.showToast({ title: '请求失败，请稍后再试', icon: 'none' });
      }
    }
  }
}
</script>

<style>
.role-list {
  margin-top: 10px;
}

.role-item {
  display: flex;
  justify-content: space-between; /* 均匀分布角色名称、描述和按钮 */
  align-items: center; /* 垂直居中 */
  padding: 10px;
  border-bottom: 1px solid #ccc; /* 每项之间分隔线 */
}

.role-name {
  flex: 1; /* 角色名称占据剩余空间 */
  font-size: 16px; /* 调整字体大小 */
}

.role-description {
  flex: 2; /* 角色描述占据多一点的空间 */
  font-size: 14px; /* 调整字体大小 */
}

.button-sp-area {
  display: flex;
  justify-content: space-between; /* 均匀分布按钮 */
  align-items: center; /* 垂直居中按钮 */
}

.mini-btn {
  margin-left: 5px; /* 按钮之间的间距 */
  font-size: 12px; /* 调整字体大小 */
}

.add-role-btn {
  margin: 10px;
  background-color: #007AFF; /* 自定义背景颜色 */
  color: white; /* 自定义文字颜色 */
  padding: 5px 10px; /* 调整内边距 */
  border-radius: 5px; /* 圆角效果 */
  font-size: 14px; /* 调整字体大小 */
}

.modal-content {
  padding: 20px;
}

.modal-title {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 15px;
}

.input-field {
  margin-bottom: 15px; /* 输入框之间的间隔 */
}

.modal-buttons {
  display: flex;
  justify-content: space-between;
  margin-top: 20px;
}
</style>
