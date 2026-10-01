<template>
  <div class="page">
    <div class="hero">
      <button class="back" type="button" @click="goHome">返回</button>
      <div class="title">羽毛球计费</div>
      <button class="add" type="button" @click="goEdit()">新建</button>
    </div>

    <div class="date-modes">
      <button type="button" :class="{ on: dateMode === 'day' }" @click="setDateMode('day')">单日</button>
      <button type="button" :class="{ on: dateMode === 'range' }" @click="setDateMode('range')">区间</button>
      <button type="button" :class="{ on: dateMode === 'all' }" @click="setDateMode('all')">全部</button>
    </div>
    <div v-if="dateMode === 'day'" class="date-bar">
      <button type="button" @click="shift(-1)">‹</button>
      <input v-model="playDate" class="date" type="date" @change="loadList" />
      <button type="button" @click="shift(1)">›</button>
    </div>
    <div v-else-if="dateMode === 'range'" class="date-bar range">
      <input v-model="dateStart" class="date" type="date" @change="loadList" />
      <span class="to">至</span>
      <input v-model="dateEnd" class="date" type="date" @change="loadList" />
    </div>

    <input v-model="keyword" class="search" placeholder="搜标题" @keyup.enter="loadList" />

    <div class="summary">
      <span>共 {{ records.length }} 局</span>
      <span>合计 {{ formatMoney(dayTotal) }}</span>
    </div>

    <div v-if="loading" class="empty">加载中...</div>
    <div v-else-if="records.length === 0" class="empty">暂无球局，点右上角新建</div>

    <article v-for="item in records" :key="item.id" class="card" @click="goEdit(item.id)">
      <div class="row">
        <div class="name">{{ item.title || "未填标题" }}</div>
        <div class="qty">{{ formatMoney(item.perPerson) }} / 人</div>
      </div>
      <div class="sub">{{ item.playDate || "-" }} · {{ item.participantCount || 0 }}人</div>
      <div class="sub">场地 {{ formatMoney(item.courtTotal) }} · 用球 {{ formatMoney(item.ballTotal) }} · 总计 {{ formatMoney(item.grandTotal) }}</div>
    </article>
  </div>
</template>

<script>
import api from "@/apiUtils/index.js";
import { canUseCrab, getUserInfo, homePath } from "@/utils/auth.js";
import { formatMoney, shiftDay, todayStr } from "@/utils/badminton-bill.js";

export default {
  data() {
    return {
      playDate: todayStr(),
      dateMode: "all",
      dateStart: todayStr(),
      dateEnd: todayStr(),
      keyword: "",
      loading: false,
      records: [],
    };
  },
  computed: {
    dayTotal() {
      return this.records.reduce((sum, item) => sum + Number(item.grandTotal || 0), 0);
    },
  },
  mounted() {
    if (!canUseCrab(getUserInfo())) {
      this.$router.replace(homePath(getUserInfo()));
      return;
    }
    const date = this.$route.query.date;
    if (date) {
      this.dateMode = "day";
      this.playDate = date;
    }
    this.loadList();
  },
  methods: {
    formatMoney,
    goHome() {
      this.$router.replace("/pages/home/home");
    },
    goEdit(id) {
      const query = id ? { id } : {};
      this.$router.push({ path: "/pages/badminton/edit", query });
    },
    setDateMode(mode) {
      this.dateMode = mode;
      this.loadList();
    },
    shift(delta) {
      this.playDate = shiftDay(this.playDate, delta);
      this.loadList();
    },
    queryParams() {
      const params = { current: 1, size: 200, title: this.keyword.trim() || undefined };
      if (this.dateMode === "range") {
        params.playDateStart = this.dateStart;
        params.playDateEnd = this.dateEnd;
      } else if (this.dateMode === "day") {
        params.playDate = this.playDate;
      }
      return params;
    },
    async loadList() {
      this.loading = true;
      try {
        const data = await api.listBadmintonBills(this.queryParams());
        this.records = Array.isArray(data?.records) ? data.records : Array.isArray(data) ? data : [];
      } catch (error) {
        console.error(error);
        this.records = [];
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
  padding: 12px 12px 28px;
  background: #f4f6fb;
}

.hero {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0 12px;
}

.back,
.add {
  padding: 6px 11px;
  font-size: 12px;
  color: #4080ff;
  background: #e8f0ff;
  border: none;
  border-radius: 999px;
}

.title {
  flex: 1;
  font-size: 18px;
  font-weight: 700;
  text-align: center;
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
}

.date-modes button.on {
  background: #4080ff;
  color: #fff;
}

.date-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.date-bar.range .to {
  font-size: 12px;
  color: #6b7280;
}

.date-bar button {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: #fff;
}

.date {
  flex: 1;
  height: 36px;
  padding: 0 8px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.search {
  width: 100%;
  height: 40px;
  margin-bottom: 10px;
  padding: 0 12px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
}

.summary {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 12px;
  color: #6b7280;
}

.empty {
  padding: 28px 0;
  text-align: center;
  color: #9ca3af;
}

.card {
  margin-bottom: 10px;
  padding: 14px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(17, 24, 39, 0.04);
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.name {
  font-weight: 700;
}

.qty {
  color: #0369a1;
  font-weight: 700;
}

.sub {
  margin-top: 6px;
  font-size: 12px;
  color: #6b7280;
}
</style>
