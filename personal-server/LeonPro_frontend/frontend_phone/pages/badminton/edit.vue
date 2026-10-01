<template>
  <div class="page">
    <div class="hero">
      <button class="back" type="button" @click="goBack">返回</button>
      <div class="title">{{ form.id ? "编辑球局" : "新球局" }}</div>
      <button v-if="form.id" class="danger" type="button" @click="remove">删除</button>
    </div>

    <div class="card">
      <label class="field"><span>日期</span><input v-model="form.playDate" type="date" /></label>
      <label class="field"><span>标题</span><input v-model="form.title" maxlength="100" placeholder="如 周五夜场" /></label>
      <label class="field">
        <span>参与人数</span>
        <input v-model.number="form.participantCount" type="number" min="1" inputmode="numeric" />
      </label>
    </div>

    <div class="card">
      <div class="block-head">
        <span>场地费</span>
        <button type="button" @click="addCourt">添加</button>
      </div>
      <div v-for="(item, index) in form.courtItems" :key="'c' + index" class="item">
        <div class="item-head">
          <span>场地 {{ index + 1 }}</span>
          <button type="button" class="link danger-text" @click="removeCourt(index)">删除</button>
        </div>
        <label class="field"><span>场地数量（片）</span><input v-model.number="item.courtCount" type="number" min="0" inputmode="numeric" /></label>
        <label class="field"><span>时长（小时）</span><input v-model.number="item.hours" type="number" min="0" step="0.5" inputmode="decimal" /></label>
        <label class="field"><span>单价（元/片/时）</span><input v-model.number="item.unitPrice" type="number" min="0" step="0.01" inputmode="decimal" /></label>
        <label class="field"><span>备注</span><input v-model="item.remark" maxlength="100" placeholder="场地名 / 时段" /></label>
        <div class="subtotal">小计 {{ formatMoney(courtAmount(item)) }} 元</div>
      </div>
    </div>

    <div class="card">
      <div class="block-head">
        <span>用球费用</span>
        <button type="button" @click="addBall">添加</button>
      </div>
      <div v-for="(item, index) in form.ballItems" :key="'b' + index" class="item">
        <div class="item-head">
          <span>用球 {{ index + 1 }}</span>
          <button type="button" class="link danger-text" @click="removeBall(index)">删除</button>
        </div>
        <label class="field"><span>用球品牌</span><input v-model="item.brand" maxlength="50" placeholder="如 亚狮龙7号" /></label>
        <label class="field"><span>数量</span><input v-model.number="item.quantity" type="number" min="0" inputmode="numeric" /></label>
        <label class="field"><span>单价（元）</span><input v-model.number="item.unitPrice" type="number" min="0" step="0.01" inputmode="decimal" /></label>
        <div class="subtotal">小计 {{ formatMoney(ballAmount(item)) }} 元</div>
      </div>
    </div>

    <div class="card">
      <label class="field"><span>备注</span><textarea v-model="form.remark" class="area" maxlength="500" /></label>
    </div>

    <div class="totals">
      <div><span>场地费</span><strong>{{ formatMoney(totals.courtTotal) }}</strong></div>
      <div><span>用球费用</span><strong>{{ formatMoney(totals.ballTotal) }}</strong></div>
      <div><span>总费用</span><strong>{{ formatMoney(totals.grandTotal) }}</strong></div>
      <div class="pay"><span>个人应付（{{ totals.people }}人）</span><strong>{{ formatMoney(totals.perPerson) }}</strong></div>
    </div>

    <button class="btn primary" type="button" :disabled="saving" @click="save">
      {{ saving ? "保存中..." : "保存" }}
    </button>
    <button class="btn ghost" type="button" @click="copySummary">复制账单</button>
  </div>
</template>

<script>
import api from "@/apiUtils/index.js";
import { canUseCrab, getUserInfo, homePath } from "@/utils/auth.js";
import { confirmAction, copyText, showToast } from "@/utils/ui.js";
import {
  ballAmount,
  buildSummaryText,
  courtAmount,
  emptyBall,
  emptyCourt,
  emptyForm,
  formatMoney,
  summarize,
} from "@/utils/badminton-bill.js";

export default {
  data() {
    return { saving: false, form: emptyForm() };
  },
  computed: {
    totals() {
      return summarize(this.form);
    },
  },
  mounted() {
    if (!canUseCrab(getUserInfo())) {
      this.$router.replace(homePath(getUserInfo()));
      return;
    }
    const id = this.$route.query.id;
    if (id) this.load(id);
  },
  methods: {
    formatMoney,
    courtAmount,
    ballAmount,
    goBack() {
      this.$router.replace({ path: "/pages/badminton/list", query: { date: this.form.playDate } });
    },
    addCourt() {
      this.form.courtItems.push(emptyCourt());
    },
    removeCourt(index) {
      this.form.courtItems.splice(index, 1);
    },
    addBall() {
      this.form.ballItems.push(emptyBall());
    },
    removeBall(index) {
      this.form.ballItems.splice(index, 1);
    },
    fill(data) {
      this.form = {
        id: data.id || "",
        playDate: data.playDate || this.form.playDate,
        title: data.title || "",
        participantCount: data.participantCount || 1,
        remark: data.remark || "",
        courtItems: (data.courtItems || []).map((item) => ({
          courtCount: item.courtCount ?? 0,
          hours: Number(item.hours ?? 0),
          unitPrice: Number(item.unitPrice ?? 0),
          remark: item.remark || "",
        })),
        ballItems: (data.ballItems || []).map((item) => ({
          brand: item.brand || "",
          quantity: item.quantity ?? 0,
          unitPrice: Number(item.unitPrice ?? 0),
        })),
      };
      if (!this.form.courtItems.length) this.form.courtItems = [emptyCourt()];
      if (!this.form.ballItems.length) this.form.ballItems = [emptyBall()];
    },
    async load(id) {
      try {
        const data = await api.getBadmintonBill(id);
        if (data) this.fill(data);
      } catch (error) {
        console.error(error);
        showToast("加载失败");
      }
    },
    async save() {
      if (!this.form.playDate) {
        showToast("请选择日期");
        return;
      }
      if (!this.form.participantCount || this.form.participantCount < 1) {
        showToast("请填写参与人数");
        return;
      }
      this.saving = true;
      try {
        const data = await api.saveBadmintonBill(this.form);
        this.fill(data || this.form);
        showToast("已保存");
      } catch (error) {
        console.error(error);
      } finally {
        this.saving = false;
      }
    },
    async remove() {
      if (!this.form.id) return;
      if (!confirmAction("删除球局", "确定删除这一局计费？")) return;
      try {
        await api.deleteBadmintonBills([this.form.id]);
        showToast("已删除");
        this.goBack();
      } catch (error) {
        console.error(error);
      }
    },
    copySummary() {
      copyText(buildSummaryText(this.form));
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
.danger {
  padding: 6px 11px;
  font-size: 12px;
  border: none;
  border-radius: 999px;
}

.back {
  color: #4080ff;
  background: #e8f0ff;
}

.danger {
  color: #b91c1c;
  background: #fee2e2;
}

.title {
  flex: 1;
  font-size: 18px;
  font-weight: 700;
  text-align: center;
}

.card {
  margin-bottom: 12px;
  padding: 12px;
  background: #fff;
  border-radius: 12px;
}

.block-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 700;
}

.block-head button,
.link {
  border: none;
  background: none;
  color: #4080ff;
  font-size: 13px;
}

.danger-text {
  color: #b91c1c;
}

.item {
  padding-top: 8px;
  margin-top: 8px;
  border-top: 1px solid #f3f4f6;
}

.item-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 13px;
  color: #4b5563;
}

.field {
  display: block;
  margin-bottom: 10px;
}

.field span {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: #6b7280;
}

.field input,
.area {
  width: 100%;
  height: 40px;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f5f7fb;
  font: inherit;
}

.area {
  min-height: 72px;
  height: auto;
}

.subtotal {
  margin: -2px 0 8px;
  font-size: 13px;
  color: #0369a1;
  text-align: right;
}

.totals {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 12px;
}

.totals div {
  padding: 12px;
  background: #fff;
  border-radius: 12px;
}

.totals span {
  display: block;
  font-size: 12px;
  color: #6b7280;
}

.totals strong {
  display: block;
  margin-top: 4px;
  font-size: 18px;
}

.totals .pay {
  grid-column: 1 / -1;
  background: #e0f2fe;
}

.totals .pay strong {
  color: #0369a1;
}

.btn {
  width: 100%;
  height: 44px;
  margin-top: 8px;
  border: none;
  border-radius: 8px;
  font-size: 15px;
}

.btn.primary {
  color: #fff;
  background: #4080ff;
}

.btn.ghost {
  color: #4080ff;
  background: #e8f0ff;
}
</style>
