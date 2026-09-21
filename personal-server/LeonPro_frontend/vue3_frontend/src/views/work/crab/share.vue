<template>
  <div class="share-page">
    <div class="card">
      <div class="brand">出货状态</div>
      <div v-if="loading" class="empty">加载中...</div>
      <div v-else-if="error" class="empty">{{ error }}</div>
      <template v-else>
        <div class="name">{{ view.customerName || "出货单" }}</div>
        <div class="spec">{{ view.spec || "-" }} · {{ view.quantity || 0 }}只</div>
        <p class="meta">出货日期 {{ view.shipDate || "-" }}</p>
        <p class="meta">电话 {{ view.phone || "-" }}</p>
        <p class="addr">{{ view.address || "未填写地址" }}</p>
        <div class="status">
          <span class="pill" :class="{ on: view.paid }">{{ view.paid ? "已付款" : "未付款" }}</span>
          <span class="pill" :class="{ on: view.shipped }">{{ view.shipped ? "已发货" : "未发货" }}</span>
        </div>
        <div class="track">
          <div class="label">发货单号</div>
          <div class="value">{{ view.trackingNo || "暂无" }}</div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import CrabShipmentAPI, { type CrabPublicVO } from "@/api/work/crab";

defineOptions({ name: "CrabShare" });

const route = useRoute();
const loading = ref(true);
const error = ref("");
const view = ref<CrabPublicVO>({});

onMounted(async () => {
  const publicId = String(route.params.publicId || "");
  if (!publicId) {
    loading.value = false;
    error.value = "链接无效";
    return;
  }
  try {
    view.value = (await CrabShipmentAPI.publicView(publicId)) || {};
  } catch (e: any) {
    error.value = e?.message || "记录不存在或链接已失效";
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.share-page {
  min-height: 100vh;
  padding: 32px 16px;
  background: linear-gradient(180deg, #ea580c 0%, #fb923c 36%, #f4f6fb 36%);
}
.card {
  max-width: 480px;
  margin: 0 auto;
  padding: 24px 20px;
  background: #fff;
  border-radius: 16px;
}
.brand {
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
  color: #c2410c;
  font-size: 16px;
}
.meta,
.addr {
  color: #6b7280;
  font-size: 13px;
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
}
.empty {
  padding: 24px 0;
  text-align: center;
  color: #6b7280;
}
</style>
