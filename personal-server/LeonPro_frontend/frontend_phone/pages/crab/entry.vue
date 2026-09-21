<template>
  <div class="page">
    <div class="hero">
      <button class="back" type="button" @click="goBack">返回</button>
      <div class="title">快速录入</div>
      <span class="date">{{ shipDate }}</span>
    </div>

    <div class="tabs">
      <button type="button" :class="{ on: tab === 'paste' }" @click="tab = 'paste'">粘贴识别</button>
      <button type="button" :class="{ on: tab === 'photo' }" @click="tab = 'photo'">拍照识别</button>
      <button type="button" :class="{ on: tab === 'manual' }" @click="tab = 'manual'">手动</button>
    </div>

    <div v-if="tab === 'paste'" class="card">
      <textarea
        v-model="rawText"
        class="area"
        placeholder="从微信或表格复制：序号 姓名 电话 地址 规格 数量"
        @paste="onPaste"
      />
      <button class="btn primary" type="button" :disabled="parsing" @click="parseText">
        {{ parsing ? "识别中..." : "识别文本" }}
      </button>
    </div>

    <div v-else-if="tab === 'photo'" class="card">
      <p class="hint">拍订货表照片。识别慢或不准时，用系统「提取文字」再回到粘贴。</p>
      <label class="file">
        拍照 / 相册
        <input accept="image/*" capture="environment" type="file" @change="onPhoto" />
      </label>
      <div v-if="ocrProgress != null" class="progress">识别中 {{ ocrProgress }}%</div>
    </div>

    <div v-else class="card">
      <label class="field"><span>姓名</span><input v-model="manual.customerName" placeholder="必填" /></label>
      <label class="field"><span>电话</span><input v-model="manual.phone" inputmode="tel" placeholder="11 位手机号" /></label>
      <label class="field"><span>地址</span><input v-model="manual.address" placeholder="收货地址" /></label>
      <label class="field"><span>规格</span><input v-model="manual.spec" placeholder="如 3.5母" /></label>
      <label class="field"><span>数量</span><input v-model="manual.quantity" inputmode="numeric" placeholder="只" /></label>
      <button class="btn primary" type="button" @click="pushManual">加入预览</button>
    </div>

    <div v-if="preview.length" class="preview-head">
      预览 {{ preview.length }} 条，确认后入库
      <button class="link" type="button" @click="preview = []">清空</button>
    </div>

    <article v-for="(item, index) in preview" :key="index" class="card row-card">
      <input v-model="item.customerName" class="mini" placeholder="姓名" />
      <input v-model="item.phone" class="mini" placeholder="电话" />
      <input v-model="item.address" class="mini" placeholder="地址" />
      <div class="split">
        <input v-model="item.spec" class="mini" placeholder="规格" />
        <input v-model.number="item.quantity" class="mini" inputmode="numeric" placeholder="数量" />
      </div>
      <button class="remove" type="button" @click="preview.splice(index, 1)">删除</button>
    </article>

    <button
      v-if="preview.length"
      class="btn primary sticky"
      type="button"
      :disabled="saving"
      @click="saveAll"
    >
      {{ saving ? "保存中..." : `入库 ${preview.length} 条` }}
    </button>
  </div>
</template>

<script>
import api from "@/apiUtils/index.js";
import { canUseCrab, getUserInfo, homePath } from "@/utils/auth.js";
import { parseCrabOrders, todayStr } from "@/utils/crab-parse.js";
import { showToast } from "@/utils/ui.js";

function emptyManual() {
  return { customerName: "", phone: "", address: "", spec: "", quantity: "" };
}

export default {
  data() {
    return {
      tab: "paste",
      shipDate: todayStr(),
      rawText: "",
      parsing: false,
      ocrProgress: null,
      saving: false,
      preview: [],
      manual: emptyManual(),
    };
  },
  mounted() {
    if (!canUseCrab(getUserInfo())) {
      this.$router.replace(homePath(getUserInfo()));
      return;
    }
    if (this.$route.query.date) this.shipDate = String(this.$route.query.date);
  },
  methods: {
    goBack() {
      this.$router.replace({ path: "/pages/crab/list", query: { date: this.shipDate } });
    },
    applyRows(rows) {
      const list = Array.isArray(rows) ? rows.filter((item) => item && (item.customerName || item.phone)) : [];
      if (!list.length) {
        showToast("没有识别到出货行");
        return;
      }
      this.preview = list.map((item) => ({
        customerName: item.customerName || "",
        phone: item.phone || "",
        address: item.address || "",
        spec: item.spec || "",
        quantity: item.quantity || "",
        seqNo: item.seqNo,
      }));
      showToast(`识别到 ${list.length} 条`);
    },
    async parseText() {
      const text = this.rawText.trim();
      if (!text) {
        showToast("请先粘贴文本");
        return;
      }
      this.parsing = true;
      try {
        const local = parseCrabOrders(text);
        if (local.length) {
          this.applyRows(local);
          return;
        }
        const data = await api.parseCrabText(text);
        this.applyRows(data?.records || []);
      } catch (error) {
        console.error(error);
      } finally {
        this.parsing = false;
      }
    },
    onPaste() {
      this.$nextTick(() => {
        if (this.rawText.trim()) this.parseText();
      });
    },
    async onPhoto(event) {
      const file = event.target.files && event.target.files[0];
      event.target.value = "";
      if (!file) return;
      this.ocrProgress = 0;
      try {
        const { recognizePhoto } = await import("@/utils/crab-ocr.js");
        const text = await recognizePhoto(file, (p) => {
          this.ocrProgress = p;
        });
        this.rawText = text;
        this.tab = "paste";
        this.applyRows(parseCrabOrders(text));
      } catch (error) {
        console.error(error);
        showToast("照片识别失败，请改用粘贴");
      } finally {
        this.ocrProgress = null;
      }
    },
    pushManual() {
      if (!this.manual.customerName.trim()) {
        showToast("请填写姓名");
        return;
      }
      this.preview.push({
        ...this.manual,
        quantity: this.manual.quantity === "" ? null : Number(this.manual.quantity),
      });
      this.manual = emptyManual();
      showToast("已加入预览");
    },
    async saveAll() {
      if (!this.preview.length || this.saving) return;
      this.saving = true;
      try {
        await api.batchSaveCrabShipments({
          shipDate: this.shipDate,
          records: this.preview.map((item) => ({
            ...item,
            quantity: item.quantity === "" ? null : Number(item.quantity),
          })),
        });
        showToast("已入库");
        this.preview = [];
        this.rawText = "";
        this.goBack();
      } catch (error) {
        console.error(error);
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 10px 12px 80px;
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

.date {
  font-size: 12px;
  color: #6b7280;
}

.back {
  min-width: 48px;
  padding: 6px 10px;
  font-size: 13px;
  color: #4080ff;
  background: #e8f0ff;
  border: none;
  border-radius: 999px;
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.tabs button {
  flex: 1;
  height: 36px;
  border: none;
  border-radius: 999px;
  background: #fff;
  color: #6b7280;
  font-size: 13px;
}

.tabs button.on {
  background: #4080ff;
  color: #fff;
}

.card {
  padding: 12px;
  margin-bottom: 10px;
  background: #fff;
  border-radius: 12px;
}

.area {
  width: 100%;
  min-height: 140px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f5f7fb;
  font-size: 14px;
  resize: vertical;
}

.hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
}

.file {
  display: block;
  height: 44px;
  line-height: 44px;
  text-align: center;
  color: #fff;
  background: #4080ff;
  border-radius: 8px;
}

.file input {
  display: none;
}

.progress {
  margin-top: 8px;
  font-size: 12px;
  color: #ea580c;
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
.mini {
  width: 100%;
  height: 40px;
  padding: 0 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f5f7fb;
}

.split {
  display: flex;
  gap: 8px;
}

.preview-head {
  display: flex;
  justify-content: space-between;
  margin: 6px 2px 8px;
  font-size: 13px;
  color: #374151;
}

.link,
.remove {
  border: none;
  background: none;
  color: #4080ff;
  font-size: 13px;
}

.remove {
  color: #dc2626;
  margin-top: 6px;
}

.btn {
  width: 100%;
  height: 44px;
  margin-top: 10px;
  border: none;
  border-radius: 8px;
  font-size: 15px;
}

.btn.primary {
  color: #fff;
  background: #4080ff;
}

.btn:disabled {
  opacity: 0.7;
}

.sticky {
  position: sticky;
  bottom: 12px;
}
</style>
