<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="logo">L</div>
        <div class="title">LeonPro</div>
        <div class="subtitle">个人工具平台</div>
      </div>

      <label class="field">
        <span class="label">用户名</span>
        <input v-model="form.username" class="input" placeholder="请输入用户名" autocomplete="username" />
      </label>
      <label class="field">
        <span class="label">密码</span>
        <input
          v-model="form.password"
          class="input"
          type="password"
          placeholder="请输入密码"
          autocomplete="current-password"
          @keyup.enter="handleLogin"
        />
      </label>

      <button class="submit" :disabled="loading" @click="handleLogin">
        {{ loading ? "登录中..." : "登 录" }}
      </button>
    </div>
  </div>
</template>

<script>
import { canEnterApp, consumeLogoutFlag, getUserInfo, setUserInfo } from "@/utils/auth.js";
import { showToast } from "@/utils/ui.js";

export default {
  data() {
    return {
      loading: false,
      form: { username: "", password: "" },
    };
  },
  mounted() {
    if (consumeLogoutFlag()) return;
    if (getUserInfo()?.id) {
      this.$router.replace("/pages/workspace/workspace");
    }
  },
  methods: {
    validate() {
      if (!this.form.username.trim()) {
        showToast("请输入用户名");
        return false;
      }
      if (!this.form.password) {
        showToast("请输入密码");
        return false;
      }
      if (this.form.password.length < 6) {
        showToast("密码长度不能少于6位");
        return false;
      }
      return true;
    },
    async handleLogin() {
      if (!this.validate() || this.loading) return;
      this.loading = true;
      try {
        const data = await this.$api.login(this.form);
        if (!data || !data.username) {
          showToast("登录失败，请检查用户名或密码");
          return;
        }
        if (!canEnterApp(data)) {
          showToast("仅注册码用户或 ROOT 可登录");
          return;
        }
        setUserInfo(data);
        showToast("登录成功");
        this.$router.replace("/pages/workspace/workspace");
      } catch (error) {
        console.error("登录失败", error);
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  padding: calc(60px + env(safe-area-inset-top)) 24px 40px;
  background: linear-gradient(180deg, #1d4ed8 0%, #4080ff 42%, #f4f6fb 42%);
}

.login-card {
  padding: 28px 20px 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(29, 78, 216, 0.12);
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
}

.logo {
  width: 56px;
  height: 56px;
  margin-bottom: 8px;
  border-radius: 14px;
  background: #4080ff;
  color: #fff;
  font-size: 28px;
  font-weight: 700;
  line-height: 56px;
  text-align: center;
}

.title {
  font-size: 22px;
  font-weight: 700;
  color: #1f2937;
}

.subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: #6b7280;
}

.field {
  display: block;
  margin-bottom: 14px;
}

.label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: #4b5563;
}

.input {
  width: 100%;
  height: 44px;
  padding: 0 12px;
  font-size: 15px;
  background: #f5f7fb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.submit {
  width: 100%;
  margin-top: 10px;
  height: 46px;
  font-size: 16px;
  color: #fff;
  background: #4080ff;
  border: none;
  border-radius: 8px;
}

.submit:disabled {
  opacity: 0.7;
}
</style>
