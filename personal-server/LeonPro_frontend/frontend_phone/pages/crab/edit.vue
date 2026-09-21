<template>
  <div class="page">
    <div class="hero">
      <button class="back" type="button" @click="goBack">返回</button>
      <div class="title">{{ form.id ? "出货详情" : "新增出货" }}</div>
      <button v-if="form.id" class="danger" type="button" @click="remove">删除</button>
    </div>

    <div class="card">
      <label class="field"><span>出货日期</span><input v-model="form.shipDate" type="date" /></label>
      <label class="field"><span>姓名</span><input v-model="form.customerName" placeholder="必填" /></label>
      <label class="field"><span>电话</span><input v-model="form.phone" inputmode="tel" /></label>
      <label class="field"><span>地址</span><textarea v-model="form.address" class="area" /></label>
      <label class="field"><span>规格</span><input v-model="form.spec" placeholder="如 3.5母" /></label>
      <label class="field"><span>数量</span><input v-model.number="form.quantity" inputmode="numeric" placeholder="只" /></label>
      <label class="field"><span>发货单号</span><input v-model="form.trackingNo" placeholder="快递单号" /></label>
      <div class="toggles">
        <button class="chip" :class="{ on: form.paid }" type="button" @click="form.paid = form.paid ? 0 : 1">
          {{ form.paid ? "已付款" : "未付款" }}
        </button>
        <button class="chip" :class="{ on: form.shipped }" type="button" @click="form.shipped = form.shipped ? 0 : 1">
          {{ form.shipped ? "已发货" : "未发货" }}
        </button>
      </div>
      <button class="btn primary" type="button" :disabled="saving" @click="save">
        {{ saving ? "保存中..." : "保存" }}
      </button>
      <button v-if="form.publicId" class="btn ghost" type="button" @click="share">分享这一单</button>
    </div>
  </div>
</template>

<script>
import api from "@/apiUtils/index.js";
import { canUseCrab, getUserInfo, homePath } from "@/utils/auth.js";
import { shareUrl, todayStr } from "@/utils/crab-parse.js";
import { confirmAction, copyText, showToast } from "@/utils/ui.js";

function emptyForm() {
  return {
    id: "",
    shipDate: todayStr(),
    customerName: "",
    phone: "",
    address: "",
    spec: "",
    quantity: "",
    trackingNo: "",
    paid: 0,
    shipped: 0,
    publicId: "",
  };
}

export default {
  data() {
    return { saving: false, form: emptyForm() };
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
    goBack() {
      this.$router.replace({ path: "/pages/crab/list", query: { date: this.form.shipDate } });
    },
    async load(id) {
      try {
        const data = await api.getCrabShipment(id);
        this.form = { ...emptyForm(), ...data, paid: data.paid ? 1 : 0, shipped: data.shipped ? 1 : 0 };
      } catch (error) {
        console.error(error);
      }
    },
    async save() {
      if (!this.form.customerName.trim()) {
        showToast("请填写姓名");
        return;
      }
      this.saving = true;
      try {
        const data = await api.saveCrabShipment({
          ...this.form,
          quantity: this.form.quantity === "" ? null : Number(this.form.quantity),
        });
        this.form = { ...this.form, ...data };
        showToast("已保存");
      } catch (error) {
        console.error(error);
      } finally {
        this.saving = false;
      }
    },
    async remove() {
      if (!this.form.id) return;
      if (!confirmAction("删除出货单", `确定删除「${this.form.customerName}」？`)) return;
      try {
        await api.deleteCrabShipments([this.form.id]);
        showToast("已删除");
        this.goBack();
      } catch (error) {
        console.error(error);
      }
    },
    async share() {
      const url = shareUrl(this.form.publicId);
      if (!url) return;
      const title = `${this.form.customerName || "出货单"}的螃蟹出货状态`;
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
.danger {
  min-width: 48px;
  padding: 6px 10px;
  font-size: 13px;
  border: none;
  border-radius: 999px;
}

.back {
  color: #4080ff;
  background: #e8f0ff;
}

.danger {
  color: #dc2626;
  background: #fee2e2;
}

.card {
  padding: 12px;
  background: #fff;
  border-radius: 12px;
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

.toggles {
  display: flex;
  gap: 8px;
  margin: 6px 0 12px;
}

.chip {
  flex: 1;
  height: 36px;
  border: none;
  border-radius: 999px;
  background: #f3f4f6;
  color: #6b7280;
}

.chip.on {
  background: #dcfce7;
  color: #166534;
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
