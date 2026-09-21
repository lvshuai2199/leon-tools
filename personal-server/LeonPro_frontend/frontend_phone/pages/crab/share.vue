<template>
  <div class="page">
    <div class="card">
      <div class="brand">出货状态</div>
      <div v-if="loading" class="empty">加载中...</div>
      <div v-else-if="error" class="empty">{{ error }}</div>
      <div v-else>
        <div class="name">{{ view.customerName || "出货单" }}</div>
        <div class="spec">{{ view.spec || "-" }} · {{ view.quantity || 0 }}只</div>
        <div class="meta">出货日期 {{ view.shipDate || "-" }}</div>
        <div class="meta">电话 {{ view.phone || "-" }}</div>
        <div class="addr">{{ view.address || "未填写地址" }}</div>
        <div class="status">
          <span class="pill" :class="{ on: view.paid }">{{ view.paid ? "已付款" : "未付款" }}</span>
          <span class="pill" :class="{ on: view.shipped }">{{ view.shipped ? "已发货" : "未发货" }}</span>
        </div>
        <div class="track">
          <div class="label">发货单号</div>
          <div class="value">{{ view.trackingNo || "暂无" }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import api from "@/apiUtils/index.js";

export default {
  data() {
    return { loading: true, error: "", view: {} };
  },
  mounted() {
    this.load();
  },
  methods: {
    async load() {
      const id = this.$route.query.id;
      if (!id) {
        this.loading = false;
        this.error = "链接无效";
        return;
      }
      try {
        this.view = (await api.publicCrabShipment(id)) || {};
      } catch (error) {
        this.error = error?.message || "记录不存在或链接已失效";
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 28px 16px;
  background: linear-gradient(180deg, #ea580c 0%, #fb923c 36%, #f4f6fb 36%);
}

.card {
  padding: 22px 18px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 28px rgba(234, 88, 12, 0.16);
}

.brand {
  font-size: 13px;
  color: #ea580c;
  font-weight: 700;
}

.name {
  margin-top: 10px;
  font-size: 24px;
  font-weight: 800;
}

.spec {
  margin-top: 6px;
  font-size: 16px;
  color: #c2410c;
}

.meta,
.addr {
  margin-top: 8px;
  font-size: 13px;
  color: #6b7280;
  word-break: break-all;
}

.status {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}

.pill {
  flex: 1;
  height: 40px;
  line-height: 40px;
  text-align: center;
  border-radius: 999px;
  background: #f3f4f6;
  color: #6b7280;
  font-weight: 600;
}

.pill.on {
  background: #dcfce7;
  color: #166534;
}

.track {
  margin-top: 16px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 10px;
}

.label {
  font-size: 12px;
  color: #9ca3af;
}

.value {
  margin-top: 4px;
  font-size: 18px;
  font-weight: 700;
  word-break: break-all;
}

.empty {
  padding: 24px 0;
  text-align: center;
  color: #6b7280;
}
</style>
