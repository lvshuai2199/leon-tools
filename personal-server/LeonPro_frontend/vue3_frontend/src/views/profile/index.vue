<template>
  <div class="app-container">
    <el-card shadow="never" class="profile-card">
      <template #header>
        <span>账号信息</span>
      </template>

      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户名">{{ user.username || "-" }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ user.nickname || "-" }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ user.email || "-" }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ user.roleName || user.roles?.join(" / ") || "-" }}</el-descriptions-item>
      </el-descriptions>

      <div class="profile-actions">
        <el-button type="primary" @click="openProfileDialog">编辑资料</el-button>
        <el-button @click="openPasswordDialog">修改密码</el-button>
      </div>
    </el-card>

    <el-dialog v-model="profileDialog.visible" title="编辑资料" width="420px" destroy-on-close>
      <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="80px">
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="profileForm.nickname" placeholder="昵称" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="profileForm.email" placeholder="邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitProfile">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialog.visible" title="修改密码" width="420px" destroy-on-close>
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="90px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="passwordForm.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            show-password
            placeholder="再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitPassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from "element-plus";
import UserAPI, { type UserForm } from "@/api/system/user";
import { useUserStore } from "@/store";

defineOptions({
  name: "Profile",
});

const userStore = useUserStore();
const user = computed(() => userStore.userInfo);

const submitLoading = ref(false);
const profileFormRef = ref<FormInstance>();
const passwordFormRef = ref<FormInstance>();

const profileDialog = reactive({ visible: false });
const passwordDialog = reactive({ visible: false });

const profileForm = reactive({
  nickname: "",
  email: "",
});

const passwordForm = reactive({
  password: "",
  confirmPassword: "",
});

const profileRules: FormRules = {
  email: [{ type: "email", message: "邮箱格式不正确", trigger: "blur" }],
};

const passwordRules: FormRules = {
  password: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    { min: 6, message: "密码长度不能少于 6 位", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, message: "请再次输入新密码", trigger: "blur" },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== passwordForm.password) {
          callback(new Error("两次输入的密码不一致"));
          return;
        }
        callback();
      },
      trigger: "blur",
    },
  ],
};

function openProfileDialog() {
  profileForm.nickname = user.value.nickname || "";
  profileForm.email = user.value.email || "";
  profileDialog.visible = true;
}

function openPasswordDialog() {
  passwordForm.password = "";
  passwordForm.confirmPassword = "";
  passwordDialog.visible = true;
}

function buildSavePayload(extra: Partial<UserForm> = {}): UserForm {
  return {
    id: user.value.id,
    username: user.value.username || "",
    nickname: extra.nickname ?? user.value.nickname,
    email: extra.email ?? user.value.email,
    roleId: user.value.roleId,
    ...extra,
  };
}

function submitProfile() {
  profileFormRef.value?.validate((valid) => {
    if (!valid) return;
    submitLoading.value = true;
    UserAPI.saveOrUpdate(buildSavePayload({ nickname: profileForm.nickname, email: profileForm.email }))
      .then((msg) => {
        ElMessage.success(typeof msg === "string" && msg ? msg : "资料已更新");
        profileDialog.visible = false;
        userStore.userInfo.nickname = profileForm.nickname;
        userStore.userInfo.email = profileForm.email;
      })
      .finally(() => {
        submitLoading.value = false;
      });
  });
}

function submitPassword() {
  passwordFormRef.value?.validate((valid) => {
    if (!valid) return;
    submitLoading.value = true;
    UserAPI.saveOrUpdate(buildSavePayload({ password: passwordForm.password }))
      .then((msg) => {
        ElMessage.success(typeof msg === "string" && msg ? msg : "密码已更新");
        passwordDialog.visible = false;
      })
      .finally(() => {
        submitLoading.value = false;
      });
  });
}
</script>

<style lang="scss" scoped>
.app-container {
  padding: 16px;
}

.profile-card {
  max-width: 640px;
}

.profile-actions {
  margin-top: 16px;
}
</style>
