<template>
  <div class="app-container">
    <el-card shadow="never" class="mb-4">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="日期">
          <el-radio-group v-model="dateMode" class="mr-2" @change="onDateModeChange">
            <el-radio-button value="day">单日</el-radio-button>
            <el-radio-button value="range">区间</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="dateMode === 'day'" label="出货日期">
          <el-date-picker
            v-model="queryParams.shipDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="全部日期"
            clearable
          />
        </el-form-item>
        <el-form-item v-else label="出货区间">
          <el-date-picker
            v-model="shipDateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            clearable
          />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="queryParams.customerName" placeholder="姓名" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="queryParams.phone" placeholder="电话" clearable @keyup.enter="handleQuery" />
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
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="flex-x-between">
          <span>螃蟹出货</span>
          <div>
            <el-button @click="openPaste">粘贴识别</el-button>
            <el-button type="primary" @click="openDialog()">
              <el-icon class="mr-1"><Plus /></el-icon>新增
            </el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border>
        <el-table-column prop="seqNo" label="序号" width="70" align="center" />
        <el-table-column prop="shipDate" label="日期" width="120" align="center" />
        <el-table-column prop="customerName" label="姓名" min-width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="spec" label="规格" width="90" align="center" />
        <el-table-column prop="quantity" label="数量" width="80" align="center" />
        <el-table-column label="已付款" width="90" align="center">
          <template #default="{ row }">
            <el-switch :model-value="!!row.paid" @change="(val) => toggleStatus(row, 'paid', !!val)" />
          </template>
        </el-table-column>
        <el-table-column label="已发货" width="90" align="center">
          <template #default="{ row }">
            <el-switch :model-value="!!row.shipped" @change="(val) => toggleStatus(row, 'shipped', !!val)" />
          </template>
        </el-table-column>
        <el-table-column prop="trackingNo" label="发货单号" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDialog(row)">编辑</el-button>
            <el-button type="primary" link size="small" @click="copyShare(row)">分享</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="560px" destroy-on-close @closed="resetForm">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="96px">
        <el-form-item label="出货日期" prop="shipDate">
          <el-date-picker v-model="formData.shipDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="姓名" prop="customerName">
          <el-input v-model="formData.customerName" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="formData.phone" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="formData.address" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="规格">
          <el-input v-model="formData.spec" placeholder="如 3.5母" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="formData.quantity" :min="0" />
        </el-form-item>
        <el-form-item label="发货单号">
          <el-input v-model="formData.trackingNo" placeholder="可手动输入、扫码或选图">
            <template #append>
              <el-button @click="scanTracking">扫码</el-button>
              <el-button @click="pickTrackingImage">图片</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="状态">
          <el-checkbox :model-value="!!formData.paid" @change="(v) => (formData.paid = v ? 1 : 0)">已付款</el-checkbox>
          <el-checkbox :model-value="!!formData.shipped" @change="(v) => (formData.shipped = v ? 1 : 0)">已发货</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="paste.visible" title="粘贴识别" width="640px">
      <el-input v-model="paste.text" type="textarea" :rows="10" placeholder="粘贴微信/表格文本：序号 姓名 电话 地址 规格 数量" />
      <el-table v-if="paste.rows.length" :data="paste.rows" class="mt-3" max-height="240" border>
        <el-table-column prop="customerName" label="姓名" />
        <el-table-column prop="phone" label="电话" />
        <el-table-column prop="spec" label="规格" width="90" />
        <el-table-column prop="quantity" label="数量" width="80" />
      </el-table>
      <template #footer>
        <el-button @click="paste.visible = false">取消</el-button>
        <el-button @click="runParse">识别</el-button>
        <el-button type="primary" :disabled="!paste.rows.length" :loading="paste.saving" @click="saveParsed">入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import CrabShipmentAPI, {
  crabShareUrl,
  type CrabShipmentForm,
  type CrabShipmentVO,
} from "@/api/work/crab";
import { pickTrackingNoFromImage, scanTrackingNo } from "@/utils/barcode-scan";

defineOptions({
  name: "CrabShipment",
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
const tableData = ref<CrabShipmentVO[]>([]);
const total = ref(0);
const formRef = ref();

const dateMode = ref<"day" | "range">("day");
const shipDateRange = ref<[string, string] | "">([today(), today()]);

const queryParams = reactive({
  current: 1,
  size: 10,
  shipDate: today(),
  customerName: "",
  phone: "",
});

const dialog = reactive({ visible: false, title: "" });
const formData = reactive<CrabShipmentForm>({
  shipDate: today(),
  customerName: "",
  phone: "",
  address: "",
  spec: "",
  quantity: undefined,
  trackingNo: "",
  paid: 0,
  shipped: 0,
});

const rules = {
  customerName: [{ required: true, message: "请填写姓名", trigger: "blur" }],
};

const paste = reactive({
  visible: false,
  text: "",
  rows: [] as CrabShipmentForm[],
  saving: false,
});

function listQuery() {
  if (dateMode.value === "range") {
    const range = Array.isArray(shipDateRange.value) ? shipDateRange.value : [];
    return {
      ...queryParams,
      shipDate: undefined,
      shipDateStart: range[0],
      shipDateEnd: range[1],
    };
  }
  return {
    ...queryParams,
    shipDateStart: undefined,
    shipDateEnd: undefined,
  };
}

function loadData() {
  loading.value = true;
  CrabShipmentAPI.getPage(listQuery())
    .then((data) => {
      tableData.value = data.records || [];
      total.value = data.total || 0;
    })
    .catch((error) => console.error(error))
    .finally(() => {
      loading.value = false;
    });
}

function handleQuery() {
  queryParams.current = 1;
  loadData();
}

function resetQuery() {
  dateMode.value = "day";
  queryParams.customerName = "";
  queryParams.phone = "";
  queryParams.shipDate = today();
  shipDateRange.value = [today(), today()];
  handleQuery();
}

function onDateModeChange() {
  if (dateMode.value === "range") {
    const day = queryParams.shipDate || today();
    shipDateRange.value = [day, day];
  } else if (Array.isArray(shipDateRange.value) && shipDateRange.value[0]) {
    queryParams.shipDate = shipDateRange.value[0];
  }
}

function defaultShipDate() {
  if (dateMode.value === "range" && Array.isArray(shipDateRange.value)) {
    return shipDateRange.value[1] || shipDateRange.value[0] || today();
  }
  return queryParams.shipDate || today();
}

function openDialog(row?: CrabShipmentVO) {
  if (row) {
    dialog.title = "编辑出货单";
    Object.assign(formData, {
      id: row.id,
      shipDate: row.shipDate,
      customerName: row.customerName,
      phone: row.phone,
      address: row.address,
      spec: row.spec,
      quantity: row.quantity,
      trackingNo: row.trackingNo,
      paid: row.paid ? 1 : 0,
      shipped: row.shipped ? 1 : 0,
    });
  } else {
    dialog.title = "新增出货单";
  }
  dialog.visible = true;
}

function resetForm() {
  formRef.value?.resetFields?.();
  formData.id = undefined;
  formData.customerName = "";
  formData.phone = "";
  formData.address = "";
  formData.spec = "";
  formData.quantity = undefined;
  formData.trackingNo = "";
  formData.paid = 0;
  formData.shipped = 0;
  formData.shipDate = defaultShipDate();
}

function applyTracking(code?: string) {
  if (!code) {
    ElMessage.warning("没有识别到单号");
    return;
  }
  formData.trackingNo = code;
  if (!formData.shipped) formData.shipped = 1;
  ElMessage.success("已填入单号");
}

function scanTracking() {
  scanTrackingNo()
    .then((code) => {
      if (!code) return;
      applyTracking(code);
    })
    .catch((error) => {
      if (error && error.name === "AbortError") return;
      console.error(error);
      ElMessage.error("扫码失败，可改用图片识别");
    });
}

function pickTrackingImage() {
  ElMessage.info("正在识别图片...");
  pickTrackingNoFromImage()
    .then((code) => applyTracking(code))
    .catch((error) => {
      console.error(error);
      ElMessage.error("图片识别失败");
    });
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    submitLoading.value = true;
    CrabShipmentAPI.save({ ...formData })
      .then(() => {
        ElMessage.success("保存成功");
        dialog.visible = false;
        loadData();
      })
      .catch((error) => console.error(error))
      .finally(() => {
        submitLoading.value = false;
      });
  });
}

function toggleStatus(row: CrabShipmentVO, field: "paid" | "shipped", val: boolean) {
  if (!row.id) return;
  CrabShipmentAPI.updateStatus({ id: row.id, [field]: val ? 1 : 0 })
    .then((data) => Object.assign(row, data))
    .catch((error) => console.error(error));
}

function copyShare(row: CrabShipmentVO) {
  const url = crabShareUrl(row);
  if (!url) {
    ElMessage.warning("暂无分享链接");
    return;
  }
  navigator.clipboard.writeText(url).then(
    () => ElMessage.success("已复制分享链接"),
    () => ElMessage.warning(url)
  );
}

function handleDelete(row: CrabShipmentVO) {
  if (!row.id) return;
  ElMessageBox.confirm(`确认删除「${row.customerName}」吗？`, "警告", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      CrabShipmentAPI.deleteByIds([row.id!]).then(() => {
        ElMessage.success("删除成功");
        loadData();
      });
    })
    .catch(() => {});
}

function openPaste() {
  paste.visible = true;
  paste.text = "";
  paste.rows = [];
}

function runParse() {
  CrabShipmentAPI.parse(paste.text).then((data) => {
    paste.rows = data.records || [];
    ElMessage.success(`识别到 ${paste.rows.length} 条`);
  });
}

function saveParsed() {
  paste.saving = true;
  CrabShipmentAPI.batchSave({ shipDate: defaultShipDate(), records: paste.rows })
    .then(() => {
      ElMessage.success("已入库");
      paste.visible = false;
      loadData();
    })
    .catch((error) => console.error(error))
    .finally(() => {
      paste.saving = false;
    });
}

onMounted(loadData);
</script>

<style lang="scss" scoped>
.app-container {
  padding: 16px;
}
</style>
