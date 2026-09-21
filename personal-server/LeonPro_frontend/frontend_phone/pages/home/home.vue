<template>
  <div class="page">
    <div class="hero">
      <div>
        <div class="hello">你好，{{ displayName }}</div>
        <div class="desc">今日出货与常用工具</div>
      </div>
      <button class="logout" type="button" @click="handleLogout">退出</button>
    </div>

    <button class="tile" type="button" @click="go('/pages/crab/list')">
      <div class="icon crab">蟹</div>
      <div class="meta">
        <div class="title">螃蟹出货</div>
        <div class="hint">粘贴或拍照录入，标记付款发货，单条分享</div>
      </div>
    </button>

    <button class="tile" type="button" @click="go('/pages/workspace/workspace')">
      <div class="icon key">码</div>
      <div class="meta">
        <div class="title">注册码生成</div>
        <div class="hint">为客户生成对应注册码</div>
      </div>
    </button>
  </div>
</template>

<script>
import { canUseCrab, clearUserInfo, getUserInfo } from "@/utils/auth.js";
import { confirmAction } from "@/utils/ui.js";

export default {
  data() {
    return { user: null };
  },
  computed: {
    displayName() {
      return this.user?.nickname || this.user?.username || "用户";
    },
  },
  mounted() {
    const user = getUserInfo();
    if (!canUseCrab(user)) {
      this.$router.replace("/pages/workspace/workspace");
      return;
    }
    this.user = user;
  },
  methods: {
    go(path) {
      this.$router.push(path);
    },
    handleLogout() {
      if (!confirmAction("退出登录", "确定退出当前账号？")) return;
      clearUserInfo();
      this.$router.replace("/pages/login/login");
    },
  },
};
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 12px 12px 28px;
  background: #f4f6fb;
}

.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 4px 16px;
}

.hello {
  font-size: 18px;
  font-weight: 700;
}

.desc {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}

.logout {
  padding: 6px 11px;
  font-size: 12px;
  color: #4080ff;
  background: #e8f0ff;
  border: none;
  border-radius: 999px;
}

.tile {
  display: flex;
  gap: 12px;
  width: 100%;
  margin-bottom: 12px;
  padding: 16px 14px;
  text-align: left;
  background: #fff;
  border: none;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(17, 24, 39, 0.04);
}

.icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  line-height: 46px;
  text-align: center;
}

.icon.crab {
  background: #ea580c;
}

.icon.key {
  background: #4080ff;
}

.title {
  font-size: 16px;
  font-weight: 700;
}

.hint {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
}
</style>
