<template>
  <div class="page">
    <div class="hero">
      <button class="back" type="button" @click="goHome">返回</button>
      <div class="title">螃蟹出货</div>
      <div class="hero-actions">
        <button class="add" type="button" @click="toggleSelect">{{ selecting ? "取消" : "选择" }}</button>
        <button v-if="!selecting" class="add" type="button" @click="goEntry">录入</button>
      </div>
    </div>

    <div class="date-modes">
      <button type="button" :class="{ on: dateMode === 'day' }" @click="setDateMode('day')">单日</button>
      <button type="button" :class="{ on: dateMode === 'range' }" @click="setDateMode('range')">区间</button>
    </div>
    <div v-if="dateMode === 'day'" class="date-bar">
      <button type="button" @click="shift(-1)">‹</button>
      <input v-model="shipDate" class="date" type="date" @change="loadList" />
      <button type="button" @click="shift(1)">›</button>
    </div>
    <div v-else class="date-bar range">
      <input v-model="dateStart" class="date" type="date" @change="onRangeChange" />
      <span class="to">至</span>
      <input v-model="dateEnd" class="date" type="date" @change="onRangeChange" />
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
    <div v-else-if="records.length === 0" class="empty">{{ emptyHint }}</div>

    <article
      v-for="item in records"
      :key="item.id"
      class="card"
      :class="{ picked: selectedIds.includes(item.id) }"
      @click="onCardClick(item)"
    >
      <label v-if="selecting" class="check" @click.stop>
        <input type="checkbox" :checked="selectedIds.includes(item.id)" @change="toggleItem(item)" />
      </label>
      <div class="row">
        <div class="name">{{ item.seqNo || "-" }}. {{ item.customerName || "未填姓名" }}</div>
        <div class="qty">{{ item.spec || "-" }} · {{ item.quantity || 0 }}只</div>
      </div>
      <div v-if="dateMode === 'range'" class="sub">日期 {{ item.shipDate || "-" }}</div>
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
        <button class="chip ghost" type="button" @click="scanTrack(item)">扫单号</button>
        <button class="chip ghost" type="button" @click="pickTrack(item)">图片</button>
        <button class="chip ghost" type="button" @click="share(item)">分享</button>
      </div>
    </article>

    <div v-if="selecting" class="dock">
      <button class="dock-btn ghost" type="button" @click="toggleAll">
        {{ allSelected ? "取消全选" : "全选" }}
      </button>
      <button class="dock-btn" type="button" :disabled="!selectedIds.length || exporting" @click="exportSheet">
        {{ exporting ? "生成中..." : `导出发货图 (${selectedIds.length})` }}
      </button>
    </div>
  </div>
</template>

<script>
import api from "@/apiUtils/index.js";
import { canUseCrab, getUserInfo, homePath } from "@/utils/auth.js";
import { copyText, showToast } from "@/utils/ui.js";
import { shareUrl, shiftDay, todayStr } from "@/utils/crab-parse.js";
import { pickTrackingNoFromImage, scanTrackingNo } from "@/utils/barcode-scan.js";
import { canvasToBlob, renderShipSheet, showSheetPreview } from "@/utils/crab-ship-sheet.js";

export default {
  data() {
    return {
      shipDate: todayStr(),
      dateMode: "day",
      dateStart: todayStr(),
      dateEnd: todayStr(),
      keyword: "",
      loading: false,
      records: [],
      selecting: false,
      selectedIds: [],
      exporting: false,
    };
  },
  computed: {
    unpaid() {
      return this.records.filter((item) => !item.paid).length;
    },
    unshipped() {
      return this.records.filter((item) => !item.shipped).length;
    },
    allSelected() {
      return this.records.length > 0 && this.selectedIds.length === this.records.length;
    },
    dateLabel() {
      if (this.dateMode === "range") {
        if (this.dateStart && this.dateEnd && this.dateStart !== this.dateEnd) {
          return `${this.dateStart}至${this.dateEnd}`;
        }
        return this.dateStart || this.dateEnd || this.shipDate;
      }
      return this.shipDate;
    },
    emptyHint() {
      return this.dateMode === "range"
        ? "这段时间还没有出货单，点右上角快速录入"
        : "这一天还没有出货单，点右上角快速录入";
    },
  },
  mounted() {
    if (!canUseCrab(getUserInfo())) {
      this.$router.replace(homePath(getUserInfo()));
      return;
    }
    const q = this.$route.query;
    if (q.start || q.end) {
      this.dateMode = "range";
      this.dateStart = String(q.start || q.date || todayStr());
      this.dateEnd = String(q.end || this.dateStart);
      this.shipDate = this.dateStart;
    } else if (q.date) {
      this.shipDate = String(q.date);
      this.dateStart = this.shipDate;
      this.dateEnd = this.shipDate;
    }
    this.loadList();
  },
  methods: {
    goHome() {
      this.$router.replace("/pages/home/home");
    },
    goEntry() {
      this.$router.push({ path: "/pages/crab/entry", query: { date: this.entryDate() } });
    },
    openEdit(item) {
      this.$router.push({ path: "/pages/crab/edit", query: { id: item.id } });
    },
    toggleSelect() {
      this.selecting = !this.selecting;
      if (!this.selecting) this.selectedIds = [];
    },
    onCardClick(item) {
      if (this.selecting) this.toggleItem(item);
      else this.openEdit(item);
    },
    toggleItem(item) {
      const id = item.id;
      const idx = this.selectedIds.indexOf(id);
      if (idx >= 0) this.selectedIds.splice(idx, 1);
      else this.selectedIds.push(id);
    },
    toggleAll() {
      if (this.allSelected) this.selectedIds = [];
      else this.selectedIds = this.records.map((item) => item.id);
    },
    async exportSheet() {
      const rows = this.records.filter((item) => this.selectedIds.includes(item.id));
      if (!rows.length) {
        showToast("请先选择出货单");
        return;
      }
      this.exporting = true;
      try {
        const canvas = renderShipSheet(rows, { date: this.dateLabel });
        const blob = await canvasToBlob(canvas);
        const filename = `螃蟹发货清单-${this.dateLabel}.png`;
        await showSheetPreview(blob, {
          filename,
          onDownloadFallback: () => showToast("已下载，也可长按预览图存入相册"),
        });
      } catch (error) {
        console.error(error);
        showToast("生成发货图失败");
      } finally {
        this.exporting = false;
      }
    },
    entryDate() {
      return this.dateMode === "range" ? this.dateEnd || this.dateStart || this.shipDate : this.shipDate;
    },
    setDateMode(mode) {
      this.dateMode = mode;
      if (mode === "range") {
        this.dateStart = this.dateStart || this.shipDate;
        this.dateEnd = this.dateEnd || this.shipDate;
      } else {
        this.shipDate = this.dateStart || this.shipDate;
      }
      this.loadList();
    },
    onRangeChange() {
      if (this.dateStart && this.dateEnd && this.dateStart > this.dateEnd) {
        const swap = this.dateStart;
        this.dateStart = this.dateEnd;
        this.dateEnd = swap;
      }
      this.loadList();
    },
    shift(delta) {
      this.shipDate = shiftDay(this.shipDate, delta);
      this.dateStart = this.shipDate;
      this.dateEnd = this.shipDate;
      this.loadList();
    },
    queryParams() {
      const params = { current: 1, size: 200 };
      if (this.dateMode === "range") {
        params.shipDateStart = this.dateStart;
        params.shipDateEnd = this.dateEnd;
      } else {
        params.shipDate = this.shipDate;
      }
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
    async applyTrack(item, code) {
      if (!code) {
        showToast("没有识别到单号");
        return;
      }
      const updated = await api.updateCrabStatus({ id: item.id, trackingNo: code, shipped: 1 });
      Object.assign(item, updated || { trackingNo: code, shipped: 1 });
      showToast("已填入单号");
    },
    async scanTrack(item) {
      try {
        const code = await scanTrackingNo();
        if (!code) return;
        await this.applyTrack(item, code);
      } catch (error) {
        if (error && error.name === "AbortError") return;
        console.error(error);
        showToast("扫码失败，可改用图片");
      }
    },
    async pickTrack(item) {
      try {
        showToast("识别中...");
        const code = await pickTrackingNoFromImage();
        await this.applyTrack(item, code);
      } catch (error) {
        console.error(error);
        showToast("图片识别失败");
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

.hero-actions {
  display: flex;
  gap: 6px;
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

.date-modes {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.date-modes button {
  flex: 1;
  height: 32px;
  border: none;
  border-radius: 999px;
  background: #e5e7eb;
  color: #4b5563;
  font-size: 13px;
}

.date-modes button.on {
  background: #2563eb;
  color: #fff;
}

.date-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.date-bar.range .date {
  min-width: 0;
}

.to {
  font-size: 13px;
  color: #6b7280;
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
  position: relative;
  padding: 12px;
  margin-bottom: 10px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid transparent;
}

.card.picked {
  border-color: #2563eb;
  background: #eff6ff;
}

.check {
  position: absolute;
  top: 12px;
  right: 12px;
}

.page {
  padding-bottom: 88px;
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

.dock {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  display: flex;
  gap: 8px;
  padding: 10px 12px calc(10px + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -6px 16px rgba(15, 23, 42, 0.08);
}

.dock-btn {
  flex: 1.4;
  height: 44px;
  border: none;
  border-radius: 10px;
  background: #2563eb;
  color: #fff;
  font-size: 15px;
}

.dock-btn.ghost {
  flex: 0.8;
  background: #e5e7eb;
  color: #111827;
}

.dock-btn:disabled {
  opacity: 0.5;
}
</style>
