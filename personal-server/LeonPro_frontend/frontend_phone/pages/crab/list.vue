<template>
  <div class="page">
    <div class="hero">
      <button class="back" type="button" @click="goHome">返回</button>
      <div class="title">螃蟹出货</div>
      <button class="add" type="button" @click="goEntry">录入</button>
    </div>

    <div class="date-bar">
      <button type="button" @click="shift(-1)">‹</button>
      <input v-model="shipDate" class="date" type="date" @change="loadList" />
      <button type="button" @click="shift(1)">›</button>
    </div>

    <input
      v-model="keyword"
      class="search"
      placeholder="搜姓名 / 电话"
      @keyup.enter="loadList"
    />

    <div class="summary">
      <span>共 {{ records.length }} 单</span>
      <span>未付 {{ unpaid }}</span>
      <span>未发 {{ unshipped }}</span>
    </div>

    <div v-if="loading" class="empty">加载中...</div>
    <div v-else-if="records.length === 0" class="empty">这一天还没有出货单，点右上角快速录入</div>

    <article v-for="item in records" :key="item.id" class="card" @click="openEdit(item)">
      <div class="row">
        <div class="name">{{ item.seqNo || "-" }}. {{ item.customerName || "未填姓名" }}</div>
        <div class="qty">{{ item.spec || "-" }} · {{ item.quantity || 0 }}只</div>
      </div>
      <div class="sub">{{ item.phone || "无电话" }}</div>
      <div class="addr">{{ item.address || "无地址" }}</div>
      <div v-if="item.trackingNo" class="track">单号 {{ item.trackingNo }}</div>
      <div class="actions" @click.stop>
        <button class="chip" :class="{ on: item.paid }" type="button" @click="toggle(item, 'paid')">
          {{ item.paid ? "已付款" : "未付款" }}
        </button>
        <button class="chip" :class="{ on: item.shipped }" type="button" @click="toggle(item, 'shipped')">
          {{ item.shipped ? "已发货" : "未发货" }}
        </button>
        <button class="chip ghost" type="button" @click="share(item)">分享</button>
      </div>
    </article>
  </div>
</template>

<script>
import api from "@/apiUtils/index.js";
import { canUseCrab, getUserInfo, homePath } from "@/utils/auth.js";
import { copyText, showToast } from "@/utils/ui.js";
import { shareUrl, shiftDay, todayStr } from "@/utils/crab-parse.js";

export default {
  data() {
    return {
      shipDate: todayStr(),
      keyword: "",
      loading: false,
      records: [],
    };
  },
  computed: {
    unpaid() {
      return this.records.filter((item) => !item.paid).length;
    },
    unshipped() {
      return this.records.filter((item) => !item.shipped).length;
    },
  },
  mounted() {
    if (!canUseCrab(getUserInfo())) {
      this.$router.replace(homePath(getUserInfo()));
      return;
    }
    if (this.$route.query.date) this.shipDate = String(this.$route.query.date);
    this.loadList();
  },
  methods: {
    goHome() {
      this.$router.replace("/pages/home/home");
    },
    goEntry() {
      this.$router.push({ path: "/pages/crab/entry", query: { date: this.shipDate } });
    },
    openEdit(item) {
      this.$router.push({ path: "/pages/crab/edit", query: { id: item.id } });
    },
    shift(delta) {
      this.shipDate = shiftDay(this.shipDate, delta);
      this.loadList();
    },
    queryParams() {
      const params = { current: 1, size: 200, shipDate: this.shipDate };
      const key = this.keyword.trim();
      if (key) {
        if (/^\d+$/.test(key)) params.phone = key;
        else params.customerName = key;
      }
      return params;
    },
    async loadList() {
      this.loading = true;
      try {
        const data = await api.listCrabShipments(this.queryParams());
        this.records = Array.isArray(data?.records) ? data.records : Array.isArray(data) ? data : [];
      } catch (error) {
        console.error(error);
        this.records = [];
      } finally {
        this.loading = false;
      }
    },
    async toggle(item, field) {
      const next = item[field] ? 0 : 1;
      try {
        const updated = await api.updateCrabStatus({ id: item.id, [field]: next });
        Object.assign(item, updated || { [field]: next });
      } catch (error) {
        console.error(error);
      }
    },
    async share(item) {
      const url = shareUrl(item.publicId);
      if (!url) {
        showToast("暂无分享链接");
        return;
      }
      const title = `${item.customerName || "出货单"}的螃蟹出货状态`;
      if (navigator.share) {
        try {
          await navigator.share({ title, url, text: title });
          return;
        } catch (error) {
          if (error && error.name === "AbortError") return;
        }
      }
      await copyText(url);
    },
  },
};
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 10px 12px 28px;
  background: #f4f6fb;
}

.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0 10px;
}

.title {
  font-size: 17px;
  font-weight: 700;
}

.back,
.add {
  min-width: 48px;
  padding: 6px 10px;
  font-size: 13px;
  color: #4080ff;
  background: #e8f0ff;
  border: none;
  border-radius: 999px;
}

.date-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.date-bar button {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: #fff;
  font-size: 18px;
}

.date,
.search {
  flex: 1;
  height: 36px;
  padding: 0 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  font-size: 14px;
}

.search {
  width: 100%;
  height: 40px;
  margin-bottom: 10px;
}

.summary {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 12px;
  color: #6b7280;
}

.card {
  padding: 12px;
  margin-bottom: 10px;
  background: #fff;
  border-radius: 12px;
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.name {
  font-size: 16px;
  font-weight: 700;
}

.qty {
  color: #ea580c;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.sub,
.addr,
.track {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
  word-break: break-all;
}

.actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.chip {
  flex: 1;
  height: 32px;
  border: none;
  border-radius: 999px;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 12px;
}

.chip.on {
  background: #dcfce7;
  color: #166534;
}

.chip.ghost {
  background: #e8f0ff;
  color: #4080ff;
}

.empty {
  padding: 28px 8px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}
</style>
