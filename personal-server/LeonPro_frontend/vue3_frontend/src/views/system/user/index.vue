<template>
  <div class="app-container">
    <el-card shadow="never" class="mb-4">
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="用户名">
          <el-input
            v-model="queryParams.username"
            placeholder="用户名"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon class="mr-1"><Search /></el-icon>查询
          </el-button>
          <el-button @click="resetQuery">
            <el-icon class="mr-1"><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="flex-x-between">
          <span>用户列表</span>
          <el-button type="primary" @click="openDialog()">
            <el-icon class="mr-1"><Plus /></el-icon>新增用户
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="userList" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="roleId" label="角色ID" width="280" align="center" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="160" align="center" />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <Pagination
        v-if="total > 0"
        v-model:page="queryParams.current"
        v-model:limit="queryParams.size"
        :total="total"
        @pagination="loadUsers"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.title"
      width="480px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="formData.username" placeholder="用户名（3-20个字符）" />
        </el-form-item>
        <el-form-item v-if="!formData.id" label="密码" prop="password">
          <el-input
            v-model="formData.password"
            type="password"
            show-password
            placeholder="密码（至少6位）"
          />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="formData.email" placeholder="邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import UserAPI, { type UserPageVO, type UserForm } from "@/api/system/user";

defineOptions({
  name: "User",
  inheritAttrs: false,
});

const loading = ref(false);
const submitLoading = ref(false);
const userList = ref<UserPageVO[]>([]);
const total = ref(0);

const queryParams = reactive({
  current: 1,
  size: 10,
  username: "",
});

const dialog = reactive({
  visible: false,
  title: "",
});

const formRef = ref();
const formData = reactive<UserForm>({
  username: "",
  password: "",
  email: "",
});

const rules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 20, message: "用户名长度须为 3-20 个字符", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, message: "密码长度不能少于6位", trigger: "blur" },
  ],
  email: [{ type: "email", message: "邮箱格式不正确", trigger: "blur" }],
};

function loadUsers() {
  loading.value = true;
  UserAPI.getPage(queryParams)
    .then((data) => {
      userList.value = data.records || [];
      total.value = data.total || 0;
    })
    .catch((error) => {
      console.error("加载用户失败", error);
    })
    .finally(() => {
      loading.value = false;
    });
}

function handleQuery() {
  queryParams.current = 1;
  loadUsers();
}

function resetQuery() {
  queryParams.username = "";
  handleQuery();
}

function openDialog(row?: UserPageVO) {
  if (row) {
    dialog.title = "编辑用户";
    Object.assign(formData, {
      id: row.id,
      username: row.username,
      email: row.email,
    });
  } else {
    dialog.title = "新增用户";
  }
  dialog.visible = true;
}

function resetForm() {
  formRef.value?.resetFields?.();
  formData.id = undefined;
  formData.username = "";
  formData.password = "";
  formData.email = "";
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    submitLoading.value = true;
    UserAPI.saveOrUpdate({ ...formData })
      .then((msg) => {
        ElMessage.success(typeof msg === "string" && msg ? msg : "保存成功");
        dialog.visible = false;
        loadUsers();
      })
      .catch((error) => {
        console.error(error);
      })
      .finally(() => {
        submitLoading.value = false;
      });
  });
}

function handleDelete(row: UserPageVO) {
  if (!row.id) return;
  ElMessageBox.confirm(`确认删除用户「${row.username}」吗？`, "警告", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      UserAPI.deleteByIds([row.id!]).then((msg) => {
        ElMessage.success(typeof msg === "string" && msg ? msg : "删除成功");
        loadUsers();
      });
    })
    .catch(() => {});
}

onMounted(() => {
  loadUsers();
});
</script>

<style lang="scss" scoped>
.app-container {
  padding: 16px;
}
</style>
