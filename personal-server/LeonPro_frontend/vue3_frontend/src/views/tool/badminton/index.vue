<template>
  <div class="app-container">
    <el-card shadow="never" class="mb-4">
      <template #header>
        <div class="flex-x-between">
          <span>{{ formData.id ? "编辑球局" : "新球局" }}</span>
          <div>
            <el-button @click="resetEditor">新建</el-button>
            <el-button @click="openSheet(formData)">结算图</el-button>
            <el-button @click="copySummary">复制账单</el-button>
            <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
          </div>
        </div>
      </template>

      <el-form :model="formData" label-width="88px" class="editor">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8">
            <el-form-item label="日期">
              <el-date-picker v-model="formData.playDate" type="date" value-format="YYYY-MM-DD" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="标题">
              <el-input v-model="formData.title" maxlength="100" placeholder="如 周五夜场 / 体育馆" :title="formData.title" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="参与人数">
              <el-input-number v-model="formData.participantCount" :min="1" :max="999" :step="1" controls-position="right" class="w-full" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="block-head">
          <span>场地费</span>
          <el-button type="primary" link @click="addCourt">
            <el-icon class="mr-1"><Plus /></el-icon>添加场地
          </el-button>
        </div>
        <el-table :data="formData.courtItems" border class="mb-4">
          <el-table-column label="场地数量（片）" min-width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.courtCount" :min="0" :max="100" :step="1" controls-position="right" class="w-full" />
            </template>
          </el-table-column>
          <el-table-column label="时长（小时）" min-width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.hours" :min="0" :max="24" :step="0.5" :precision="2" controls-position="right" class="w-full" />
            </template>
          </el-table-column>
          <el-table-column label="单价（元/片/时）" min-width="160">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :max="100000" :step="1" :precision="2" controls-position="right" class="w-full" />
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.remark" maxlength="100" placeholder="场地名 / 时段" />
            </template>
          </el-table-column>
          <el-table-column label="小计" width="120" align="right" class-name="num-cell">
            <template #default="{ row }">{{ formatMoney(courtAmount(row)) }}</template>
          </el-table-column>
          <el-table-column label="" width="70" align="center">
            <template #default="{ $index }">
              <el-button type="danger" link @click="removeCourt($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="block-head">
          <span>用球费用</span>
          <el-button type="primary" link @click="addBall">
            <el-icon class="mr-1"><Plus /></el-icon>添加用球
          </el-button>
        </div>
        <el-table :data="formData.ballItems" border class="mb-4">
          <el-table-column label="用球品牌" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.brand" maxlength="50" placeholder="如 亚狮龙7号" class="brand-input" :title="row.brand" />
            </template>
          </el-table-column>
          <el-table-column label="数量" min-width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :max="10000" :step="1" controls-position="right" class="w-full" />
            </template>
          </el-table-column>
          <el-table-column label="单价（元）" min-width="140">
            <template #default="{ row }">
              <template v-if="hasBucket(row)">
                <div class="unit-ro">¥{{ formatMoney(unitOf(row)) }}</div>
                <div class="unit-hint">
                  <span>整桶 ÷ 12</span>
                  <el-button type="primary" link class="unit-manual" @click="manualUnit(row)">改为手填</el-button>
                </div>
              </template>
              <el-input-number
                v-else
                v-model="row.unitPrice"
                :min="0"
                :max="100000"
                :step="1"
                :precision="2"
                controls-position="right"
                class="w-full"
              />
            </template>
          </el-table-column>
          <el-table-column label="整桶价" width="140">
            <template #default="{ row }">
              <el-input-number
                :model-value="row.bucketPrice ?? undefined"
                :min="0"
                :max="100000"
                :precision="2"
                :controls="false"
                :value-on-clear="null"
                class="bucket-input"
                aria-label="整桶价"
                @update:model-value="(v: number | null | undefined) => onBucketChange(row, v)"
              >
                <template #suffix>元/桶</template>
              </el-input-number>
            </template>
          </el-table-column>
          <el-table-column label="小计" width="120" align="right" class-name="num-cell">
            <template #default="{ row }">{{ formatMoney(ballAmount(row)) }}</template>
          </el-table-column>
          <el-table-column label="" width="70" align="center">
            <template #default="{ $index }">
              <el-button type="danger" link @click="removeBall($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" maxlength="500" placeholder="可选" />
        </el-form-item>
      </el-form>

      <div class="summary">
        <div class="summary-item">
          <span>场地费</span>
          <strong>{{ formatMoney(totals.courtTotal) }}</strong>
        </div>
        <div class="summary-item">
          <span>用球费用</span>
          <strong>{{ formatMoney(totals.ballTotal) }}</strong>
        </div>
        <div class="summary-item">
          <span>总费用</span>
          <strong>{{ formatMoney(totals.grandTotal) }}</strong>
        </div>
        <div class="summary-item highlight">
          <span>个人应付（{{ totals.people }}人）</span>
          <strong>{{ formatMoney(totals.perPerson) }}</strong>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="flex-x-between">
          <span>历史球局</span>
        </div>
      </template>

      <el-form :inline="true" :model="queryParams" class="mb-2">
        <el-form-item label="日期">
          <el-radio-group v-model="dateMode" class="mr-2" @change="onDateModeChange">
            <el-radio-button value="day">单日</el-radio-button>
            <el-radio-button value="range">区间</el-radio-button>
            <el-radio-button value="all">全部</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="dateMode === 'day'" label="球局日期">
          <el-date-picker v-model="queryParams.playDate" type="date" value-format="YYYY-MM-DD" placeholder="全部日期" clearable />
        </el-form-item>
        <el-form-item v-else-if="dateMode === 'range'" label="区间">
          <el-date-picker
            v-model="playDateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            clearable
          />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="queryParams.title" placeholder="标题" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon class="mr-1"><Search /></el-icon>查询
          </el-button>
          <el-button @click="resetQuery">
            <el-icon class="mr-1"><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tableData" border highlight-current-row @row-click="openRow">
        <el-table-column prop="playDate" label="日期" width="110" align="center" />
        <el-table-column prop="title" label="标题" min-width="120">
          <template #default="{ row }">
            <span class="title-cell" :title="row.title || ''">{{ row.title || "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="participantCount" label="人数" width="64" align="center" />
        <el-table-column label="场地费" width="110" align="right" class-name="num-cell">
          <template #default="{ row }">{{ formatMoney(row.courtTotal) }}</template>
        </el-table-column>
        <el-table-column label="用球费" width="110" align="right" class-name="num-cell">
          <template #default="{ row }">{{ formatMoney(row.ballTotal) }}</template>
        </el-table-column>
        <el-table-column label="总计" width="110" align="right" class-name="num-cell">
          <template #default="{ row }">{{ formatMoney(row.grandTotal) }}</template>
        </el-table-column>
        <el-table-column label="个人应付" width="110" align="right" class-name="num-cell">
          <template #default="{ row }">
            <span class="per-person">{{ formatMoney(row.perPerson) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="记录人" width="84" show-overflow-tooltip />
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <div class="lp-table-ops" @click.stop>
              <el-button type="primary" link size="small" @click="openRow(row)">编辑</el-button>
              <el-button type="primary" link size="small" @click="exportRowSheet(row)">结算图</el-button>
              <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <Pagination
        v-if="total > 0"
        v-model:page="queryParams.current"
        v-model:limit="queryParams.size"
        :total="total"
        @pagination="loadData"
      />
    </el-card>

    <el-dialog
      v-model="sheetVisible"
      title="结算明细"
      width="560px"
      append-to-body
      destroy-on-close
      @closed="revokeSheetUrl"
    >
      <p class="sheet-hint">可发给球友对账，或下载图片保存。</p>
      <div class="sheet-preview">
        <img v-if="sheetUrl" :src="sheetUrl" alt="羽毛球结算明细" />
      </div>
      <template #footer>
        <el-button @click="sheetVisible = false">关闭</el-button>
        <el-button type="primary" @click="downloadSheet">下载图片</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import BadmintonBillAPI, {
  ballAmount,
  bucketPrice,
  bucketPriceInvalid,
  bucketUnitPrice,
  buildSummaryText,
  courtAmount,
  emptyBall,
  emptyCourt,
  formatMoney,
  summarize,
  type BadmintonBallFeeItem,
  type BadmintonBillForm,
  type BadmintonBillVO,
  type BadmintonCourtFeeItem,
} from "@/api/tool/badminton";
import { copyText } from "@/utils/clipboard";
import { canvasToBlob, downloadBlob, renderBillSheet, sheetFilename } from "@/utils/badminton-bill-sheet";

defineOptions({
  name: "BadmintonBill",
  inheritAttrs: false,
});

function today() {
  const now = new Date();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${m}-${d}`;
}

const loading = ref(false);
const submitLoading = ref(false);
const tableData = ref<BadmintonBillVO[]>([]);
const total = ref(0);
const dateMode = ref<"day" | "range" | "all">("all");
const playDateRange = ref<[string, string] | "">("");

const queryParams = reactive({
  current: 1,
  size: 10,
  playDate: "",
  title: "",
});

const formData = reactive<BadmintonBillForm>({
  playDate: today(),
  title: "",
  participantCount: 4,
  remark: "",
  courtItems: [emptyCourt()],
  ballItems: [emptyBall()],
});

const totals = computed(() => summarize(formData));

function addCourt() {
  formData.courtItems = [...(formData.courtItems || []), emptyCourt()];
}

function removeCourt(index: number) {
  const list = [...(formData.courtItems || [])];
  list.splice(index, 1);
  formData.courtItems = list;
}

function addBall() {
  formData.ballItems = [...(formData.ballItems || []), emptyBall()];
}

function removeBall(index: number) {
  const list = [...(formData.ballItems || [])];
  list.splice(index, 1);
  formData.ballItems = list;
}

/* 整桶价：有值时单价只读 = round2(整桶/12)；「改为手填」清空整桶价（规则与用户端一致） */
function hasBucket(row: BadmintonBallFeeItem) {
  return bucketPrice(row) !== null;
}

function unitOf(row: BadmintonBallFeeItem) {
  const bucket = bucketPrice(row);
  return bucket === null ? Number(row.unitPrice || 0) : bucketUnitPrice(bucket);
}

function onBucketChange(row: BadmintonBallFeeItem, value: number | null | undefined) {
  row.bucketPrice = value === undefined ? null : value;
  const bucket = bucketPrice(row);
  if (bucket !== null) row.unitPrice = bucketUnitPrice(bucket);
}

function manualUnit(row: BadmintonBallFeeItem) {
  const bucket = bucketPrice(row);
  if (bucket !== null) row.unitPrice = bucketUnitPrice(bucket);
  row.bucketPrice = null;
}

function resetEditor() {
  formData.id = undefined;
  formData.playDate = today();
  formData.title = "";
  formData.participantCount = 4;
  formData.remark = "";
  formData.courtItems = [emptyCourt()];
  formData.ballItems = [emptyBall()];
}

function fillForm(row: BadmintonBillVO) {
  formData.id = row.id;
  formData.playDate = row.playDate || today();
  formData.title = row.title || "";
  formData.participantCount = row.participantCount || 1;
  formData.remark = row.remark || "";
  formData.courtItems = (row.courtItems || []).map((item: BadmintonCourtFeeItem) => ({
    courtCount: item.courtCount ?? 0,
    hours: Number(item.hours ?? 0),
    unitPrice: Number(item.unitPrice ?? 0),
    remark: item.remark || "",
  }));
  formData.ballItems = (row.ballItems || []).map((item: BadmintonBallFeeItem) => ({
    brand: item.brand || "",
    quantity: item.quantity ?? 0,
    unitPrice: Number(item.unitPrice ?? 0),
    bucketPrice: item.bucketPrice === null || item.bucketPrice === undefined ? null : Number(item.bucketPrice),
  }));
  if (!formData.courtItems.length) formData.courtItems = [emptyCourt()];
  if (!formData.ballItems.length) formData.ballItems = [emptyBall()];
}

function onDateModeChange() {
  if (dateMode.value === "day") {
    queryParams.playDate = today();
    playDateRange.value = "";
  } else if (dateMode.value === "range") {
    queryParams.playDate = "";
    playDateRange.value = [today(), today()];
  } else {
    queryParams.playDate = "";
    playDateRange.value = "";
  }
}

function loadData() {
  loading.value = true;
  const range = dateMode.value === "range" && Array.isArray(playDateRange.value) ? playDateRange.value : ["", ""];
  BadmintonBillAPI.getPage({
    ...queryParams,
    playDate: dateMode.value === "day" ? queryParams.playDate : "",
    playDateStart: range[0],
    playDateEnd: range[1],
  })
    .then((data) => {
      tableData.value = data.records || [];
      total.value = data.total || 0;
    })
    .catch((error) => {
      console.error(error);
    })
    .finally(() => {
      loading.value = false;
    });
}

function handleQuery() {
  queryParams.current = 1;
  loadData();
}

function resetQuery() {
  dateMode.value = "all";
  queryParams.playDate = "";
  queryParams.title = "";
  playDateRange.value = "";
  handleQuery();
}

function openRow(row: BadmintonBillVO) {
  if (!row.id) return;
  BadmintonBillAPI.getById(row.id)
    .then((data) => {
      fillForm(data);
      window.scrollTo({ top: 0, behavior: "smooth" });
    })
    .catch((error) => {
      console.error(error);
    });
}

function handleSubmit() {
  if (!formData.playDate) {
    ElMessage.warning("请选择日期");
    return;
  }
  if (!formData.participantCount || formData.participantCount < 1) {
    ElMessage.warning("请填写参与人数");
    return;
  }
  if ((formData.ballItems || []).some((item) => bucketPriceInvalid(item))) {
    ElMessage.warning("请输入大于 0 的价格");
    return;
  }
  // 整桶价 0 / 空 = 没填（发 null）；有整桶价时单价同步成 round2(整桶/12)，服务端也会重算
  const ballItems = (formData.ballItems || []).map((item) => {
    const bucket = bucketPrice(item);
    return { ...item, bucketPrice: bucket, unitPrice: bucket === null ? item.unitPrice : bucketUnitPrice(bucket) };
  });
  submitLoading.value = true;
  BadmintonBillAPI.save({ ...formData, ballItems })
    .then((data) => {
      ElMessage.success("保存成功");
      fillForm(data);
      loadData();
    })
    .catch((error) => {
      console.error(error);
    })
    .finally(() => {
      submitLoading.value = false;
    });
}

function handleDelete(row: BadmintonBillVO) {
  if (!row.id) return;
  ElMessageBox.confirm(`确认删除「${row.playDate || ""} ${row.title || "球局"}」吗？`, "警告", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      BadmintonBillAPI.deleteByIds([row.id!]).then(() => {
        ElMessage.success("删除成功");
        if (formData.id === row.id) resetEditor();
        loadData();
      });
    })
    .catch(() => {});
}

function copySummary() {
  copyText(buildSummaryText({ ...formData, ...totals.value }), "已复制账单");
}

const sheetVisible = ref(false);
const sheetUrl = ref("");
const sheetName = ref("羽毛球结算.png");
const sheetBlob = ref<Blob | null>(null);

function revokeSheetUrl() {
  if (sheetUrl.value) URL.revokeObjectURL(sheetUrl.value);
  sheetUrl.value = "";
  sheetBlob.value = null;
}

async function openSheet(bill: BadmintonBillForm & { operatorName?: string }) {
  try {
    const canvas = renderBillSheet(bill);
    const blob = await canvasToBlob(canvas);
    revokeSheetUrl();
    sheetBlob.value = blob;
    sheetUrl.value = URL.createObjectURL(blob);
    sheetName.value = sheetFilename(bill);
    sheetVisible.value = true;
  } catch (error) {
    console.error(error);
    ElMessage.error("生成结算图失败");
  }
}

function exportRowSheet(row: BadmintonBillVO) {
  if (!row.id) return;
  BadmintonBillAPI.getById(row.id)
    .then((data) => openSheet(data))
    .catch((error) => {
      console.error(error);
    });
}

function downloadSheet() {
  if (!sheetBlob.value) return;
  downloadBlob(sheetBlob.value, sheetName.value);
}

onUnmounted(() => {
  revokeSheetUrl();
});

onMounted(() => {
  loadData();
});
</script>

<style lang="scss" scoped>
.w-full {
  width: 100%;
}
.unit-ro {
  line-height: 32px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}
.unit-hint {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  line-height: 16px;
  color: var(--el-text-color-secondary);
}
.unit-manual {
  height: auto;
  padding: 0;
  font-size: 12px;
}
.bucket-input {
  width: 100%;
  :deep(.el-input__wrapper) {
    padding: 1px 8px;
  }
  :deep(.el-input__suffix-inner) {
    font-size: 12px;
  }
  :deep(.el-input__inner) {
    text-align: left;
  }
}
/* 防溢出（美工 10-04）：品牌单行省略 + title；数字等宽、不省略不换行；名称在列表最多两行 */
.brand-input :deep(.el-input__inner) {
  text-overflow: ellipsis;
}
.editor :deep(.el-input-number .el-input__inner),
.summary strong,
.unit-ro {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
:deep(.num-cell .cell) {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.title-cell {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  overflow-wrap: anywhere;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 10px;
  font-weight: 600;
}

.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 4px 0 0;
}

.summary-item {
  padding: 12px 14px;
  background: var(--el-fill-color-light);
  border-radius: 8px;

  span {
    display: block;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  strong {
    display: block;
    margin-top: 6px;
    font-size: 20px;
  }
}

.summary-item.highlight {
  background: var(--el-color-primary-light-9);

  strong {
    color: var(--el-color-primary);
  }
}

.per-person {
  font-weight: 600;
  color: var(--el-color-primary);
}

.sheet-hint {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.sheet-preview {
  max-height: 62vh;
  overflow: auto;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 8px;

  img {
    display: block;
    width: 100%;
    border-radius: 6px;
  }
}

@media (max-width: 900px) {
  .summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
