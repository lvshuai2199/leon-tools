<template>
  <div class="page">
    <div class="hero">
      <div>
        <div class="hello">你好，{{ displayName }}</div>
        <div class="desc">为客户生成对应注册码</div>
      </div>
      <button class="logout" type="button" @click="handleLogout">退出</button>
    </div>

    <div class="card">
      <div v-if="quota" class="quota">
        <span class="quota-label">剩余次数</span>
        <span class="quota-value">{{ quotaText }}</span>
      </div>

      <div v-if="loadingConfigs" class="empty">正在加载可用配置...</div>
      <div v-else-if="companies.length === 0" class="empty">暂无可用注册码，请联系管理员分配</div>
      <div v-else class="form">
        <label class="field">
          <span class="label">公司</span>
          <select class="picker" :value="companyName" @change="onCompanyChange">
            <option v-for="name in companies" :key="name" :value="name">{{ name }}</option>
          </select>
        </label>
        <label class="field">
          <span class="label">组件名称</span>
          <select class="picker" :value="configId" @change="onConfigChange">
            <option v-for="item in currentConfigs" :key="item.id" :value="item.id">
              {{ item.name || item.componentName || item.id }}
            </option>
          </select>
        </label>
        <label class="field">
          <span class="label">注册码</span>
          <input
            v-model="regCode"
            class="input"
            maxlength="6"
            placeholder="输入 6 位注册码"
            @keyup.enter="handleGenerate"
          />
        </label>

        <div v-for="field in allFields" :key="field" class="result-row">
          <div>
            <div class="result-label">{{ validityLabels[field] }}</div>
            <div class="result-value">{{ result[field] || "-" }}</div>
          </div>
          <button v-if="isGenerated && result[field]" class="copy" type="button" @click="copyResult(result[field])">
            复制
          </button>
        </div>

        <div class="actions">
          <button class="btn ghost" type="button" @click="resetAll">重置</button>
          <button class="btn primary" type="button" :disabled="generating" @click="handleGenerate">
            {{ generating ? "生成中..." : "生成" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { canEnterApp, getUserInfo, clearUserInfo } from "@/utils/auth.js";
import api from "@/apiUtils/index.js";
import { confirmAction, copyText, showToast } from "@/utils/ui.js";

const ALL_FIELDS = [
  "oneMonthValid",
  "twoMonthValid",
  "fourMonthValid",
  "sixMonthValid",
  "thirteenMonthValid",
  "longTimeValid",
];
const VALIDITY_LABELS = {
  oneMonthValid: "一个月",
  twoMonthValid: "两个月",
  fourMonthValid: "四个月",
  sixMonthValid: "六个月",
  thirteenMonthValid: "十三个月",
  longTimeValid: "永久",
};

function emptyResult() {
  return {
    oneMonthValid: "OneMonth",
    twoMonthValid: "TwoMonth",
    fourMonthValid: "FourMonth",
    sixMonthValid: "SixMonth",
    thirteenMonthValid: "ThirteenMonth",
    longTimeValid: "Forever",
  };
}

export default {
  data() {
    return {
      user: null,
      quota: null,
      validityLabels: VALIDITY_LABELS,
      configs: [],
      loadingConfigs: false,
      companyName: "",
      configId: "",
      regCode: "",
      generating: false,
      isGenerated: false,
      result: emptyResult(),
    };
  },
  computed: {
    api() {
      return this.$api || api;
    },
    displayName() {
      return this.user?.nickname || this.user?.username || "用户";
    },
    quotaText() {
      if (!this.quota) return "-";
      if (this.quota.unlimited) return "不限";
      return `${this.quota.remaining ?? 0} / ${this.quota.generateLimit ?? 0}`;
    },
    companies() {
      const names = [];
      this.configs.forEach((item) => {
        const company = item.company || "未分组";
        if (!names.includes(company)) names.push(company);
      });
      return names;
    },
    currentConfigs() {
      return this.configs.filter((item) => (item.company || "未分组") === this.companyName);
    },
    currentConfig() {
      return this.currentConfigs.find((item) => item.id === this.configId) || this.currentConfigs[0] || null;
    },
    allFields() {
      return ALL_FIELDS;
    },
  },
  mounted() {
    this.ensureLogin();
  },
  methods: {
    ensureLogin() {
      const user = getUserInfo();
      if (!user || !canEnterApp(user)) {
        this.leaveToLogin();
        return;
      }
      this.user = user;
      this.loadConfigs();
      this.loadQuota();
    },
    applyDefaultSelection() {
      const list = Array.isArray(this.configs) ? this.configs : [];
      const company = this.companies[0] || list[0]?.company || "";
      this.companyName = company;
      const first = list.find((item) => item.company === company) || list[0];
      this.configId = first?.id || "";
    },
    normalizeConfigs(data) {
      if (Array.isArray(data)) return data;
      if (data && Array.isArray(data.records)) return data.records;
      return [];
    },
    onCompanyChange(event) {
      this.companyName = event.target.value || "";
      this.configId = this.currentConfigs[0]?.id || "";
      this.resetResult();
    },
    onConfigChange(event) {
      this.configId = event.target.value || "";
      this.resetResult();
    },
    resetResult() {
      this.isGenerated = false;
      this.result = emptyResult();
    },
    resetAll() {
      this.regCode = "";
      this.resetResult();
    },
    async loadQuota() {
      try {
        this.quota = (await this.api.myQuota()) || null;
      } catch (error) {
        console.error(error);
      }
    },
    async loadConfigs() {
      this.loadingConfigs = true;
      try {
        this.configs = this.normalizeConfigs(await this.api.listRegCodeConfig());
        this.applyDefaultSelection();
      } catch (error) {
        console.error(error);
        this.configs = [];
      } finally {
        this.loadingConfigs = false;
      }
    },
    async handleGenerate() {
      if (!this.currentConfig?.id) {
        showToast("请选择名称");
        return;
      }
      if (!this.regCode || this.regCode.length !== 6) {
        showToast("注册码长度必须为 6 位");
        return;
      }
      if (this.quota && !this.quota.unlimited && (this.quota.remaining || 0) <= 0) {
        showToast("生成次数已用完");
        return;
      }
      this.generating = true;
      try {
        const data = await this.api.genTempRegCode({
          regCode: this.regCode,
          configId: this.currentConfig.id,
          company: this.currentConfig.company,
          applyName: this.currentConfig.name,
          applyId: this.user?.id,
        });
        this.result = { ...emptyResult(), ...(data || {}) };
        this.isGenerated = true;
        showToast("生成成功");
        this.loadQuota();
      } catch (error) {
        console.error(error);
      } finally {
        this.generating = false;
      }
    },
    async copyResult(text) {
      try {
        await copyText(text);
      } catch {
        showToast("复制失败");
      }
    },
    handleLogout() {
      if (!confirmAction("退出登录", "确定退出当前账号？")) return;
      this.leaveToLogin();
    },
    leaveToLogin() {
      this.user = null;
      clearUserInfo();
      this.$router.replace("/pages/login/login");
    },
  },
};
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 12px 12px 24px;
  background: #f4f6fb;
}

.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 4px 12px;
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

.card {
  padding: 14px;
  background: #fff;
  border-radius: 10px;
}

.quota {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  margin-bottom: 12px;
  background: #fff7ed;
  border-radius: 8px;
}

.quota-label {
  font-size: 12px;
  color: #9a3412;
}

.quota-value {
  font-size: 15px;
  font-weight: 600;
  color: #c2410c;
}

.field {
  display: block;
  margin-bottom: 12px;
}

.label {
  display: block;
  margin-bottom: 5px;
  font-size: 12px;
  color: #6b7280;
}

.input,
.picker {
  width: 100%;
  height: 42px;
  padding: 0 12px;
  font-size: 14px;
  background: #f5f7fb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.result-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 0;
  border-bottom: 1px solid #f3f4f6;
}

.result-label {
  font-size: 11px;
  color: #9ca3af;
}

.result-value {
  margin-top: 3px;
  font-size: 14px;
  color: #4080ff;
  word-break: break-all;
}

.copy {
  font-size: 13px;
  color: #4080ff;
  background: none;
  border: none;
}

.actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.btn {
  flex: 1;
  height: 42px;
  font-size: 14px;
  border: none;
  border-radius: 8px;
}

.btn.primary {
  color: #fff;
  background: #4080ff;
}

.btn.ghost {
  color: #374151;
  background: #f3f4f6;
}

.btn:disabled {
  opacity: 0.7;
}

.empty {
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}
</style>
