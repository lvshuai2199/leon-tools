<template>
  <div class="app-container">
    <el-card shadow="never" class="mb-4">
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="申请人">
          <el-input
            v-model="queryParams.applyName"
            placeholder="申请人姓名"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="公司">
          <el-input v-model="queryParams.company" placeholder="公司名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select v-model="queryParams.applyStatus" placeholder="全部" clearable class="w-32">
            <el-option label="待处理" :value="0" />
            <el-option label="已生成注册码" :value="1" />
          </el-select>
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
          <span>注册申请列表</span>
          <div>
            <el-button type="warning" plain @click="tempCodeDialog.visible = true">
              <el-icon class="mr-1"><Key /></el-icon>生成临时注册码
            </el-button>
            <el-button type="primary" @click="openDialog()">
              <el-icon class="mr-1"><Plus /></el-icon>新增申请
            </el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="regList" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="applyName" label="申请人" width="100" align="center" />
        <el-table-column prop="company" label="公司" min-width="150" show-overflow-tooltip />
        <el-table-column prop="salesName" label="销售" width="90" align="center" />
        <el-table-column prop="applyPhone" label="联系电话" width="130" align="center" />
        <el-table-column prop="regCode" label="注册码" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.regCode" type="success" size="small">{{ row.regCode }}</el-tag>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="applyStatus" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.applyStatus === 1 ? 'success' : 'warning'" size="small">
              {{ row.applyStatus === 1 ? "已生成注册码" : "待处理" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请时间" width="160" align="center" />
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.applyStatus !== 1"
              type="success"
              link
              size="small"
              @click="handleGenCode(row)"
            >
              生成注册码
            </el-button>
            <el-button type="primary" link size="small" @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <Pagination
        v-if="total > 0"
        v-model:page="queryParams.current"
        v-model:limit="queryParams.size"
        :total="total"
        @pagination="loadList"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.title"
      width="560px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="90px">
        <el-form-item label="申请人" prop="applyName">
          <el-input v-model="formData.applyName" placeholder="申请人姓名" />
        </el-form-item>
        <el-form-item label="公司" prop="company">
          <el-input v-model="formData.company" placeholder="公司名称" />
        </el-form-item>
        <el-form-item label="销售">
          <el-input v-model="formData.salesName" placeholder="跟进销售姓名" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="formData.applyPhone" placeholder="联系电话" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remarks" type="textarea" :rows="3" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 临时注册码生成弹窗 -->
    <el-dialog v-model="tempCodeDialog.visible" title="生成临时注册码" width="560px" destroy-on-close>
      <div v-if="!tempCodeResult" class="temp-code-form">
        <el-form label-width="90px">
          <el-form-item label="申请人">
            <el-input v-model="tempCodeDialog.applyName" placeholder="申请人姓名" />
          </el-form-item>
          <el-form-item label="公司">
            <el-input v-model="tempCodeDialog.company" placeholder="公司名称" />
          </el-form-item>
        </el-form>
      </div>

      <div v-else class="temp-code-result">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="一个月有效">{{ tempCodeResult.oneMonthValid }}</el-descriptions-item>
          <el-descriptions-item label="两个月有效">{{ tempCodeResult.twoMonthValid }}</el-descriptions-item>
          <el-descriptions-item label="四个月有效">{{ tempCodeResult.fourMonthValid }}</el-descriptions-item>
          <el-descriptions-item label="六个月有效">{{ tempCodeResult.sixMonthValid }}</el-descriptions-item>
          <el-descriptions-item label="十三个月有效">{{ tempCodeResult.thirteenMonthValid }}</el-descriptions-item>
          <el-descriptions-item label="永久有效">{{ tempCodeResult.longTimeValid }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <template #footer>
        <template v-if="!tempCodeResult">
          <el-button @click="tempCodeDialog.visible = false">取消</el-button>
          <el-button type="primary" :loading="tempCodeLoading" @click="handleGenTempCode">生成</el-button>
        </template>
        <template v-else>
          <el-button @click="tempCodeResult = null">重新生成</el-button>
          <el-button type="primary" @click="tempCodeDialog.visible = false">关闭</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import RegistrationAPI, {
  type RegistrationPageVO,
  type RegistrationForm,
  type TempRegCodeVO,
} from "@/api/registration";

defineOptions({
  name: "Registration",
  inheritAttrs: false,
});

const loading = ref(false);
const submitLoading = ref(false);
const regList = ref<RegistrationPageVO[]>([]);
const total = ref(0);

const queryParams = reactive({
  current: 1,
  size: 10,
  applyName: "",
  company: "",
  applyStatus: undefined as number | undefined,
});

const dialog = reactive({
  visible: false,
  title: "",
});

const formRef = ref();
const formData = reactive<RegistrationForm>({});

const rules = {
  applyName: [{ required: true, message: "请输入申请人姓名", trigger: "blur" }],
  company: [{ required: true, message: "请输入公司名称", trigger: "blur" }],
};

// 临时注册码
const tempCodeDialog = reactive({
  visible: false,
  applyName: "",
  company: "",
});
const tempCodeLoading = ref(false);
const tempCodeResult = ref<TempRegCodeVO | null>(null);

function loadList() {
  loading.value = true;
  RegistrationAPI.getPage(queryParams)
    .then((data) => {
      regList.value = data.records || [];
      total.value = data.total || 0;
    })
    .catch((error) => {
      console.error("加载注册申请失败", error);
    })
    .finally(() => {
      loading.value = false;
    });
}

function handleQuery() {
  queryParams.current = 1;
  loadList();
}

function resetQuery() {
  queryParams.applyName = "";
  queryParams.company = "";
  queryParams.applyStatus = undefined;
  handleQuery();
}

function openDialog(row?: RegistrationPageVO) {
  if (row) {
    dialog.title = "编辑注册申请";
    Object.assign(formData, row);
  } else {
    dialog.title = "新增注册申请";
  }
  dialog.visible = true;
}

function resetForm() {
  formRef.value?.resetFields?.();
  Object.keys(formData).forEach((key) => delete (formData as any)[key]);
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    submitLoading.value = true;
    const isUpdate = !!formData.id;
    const request = isUpdate ? RegistrationAPI.update(formData) : RegistrationAPI.add(formData);
    request
      .then(() => {
        ElMessage.success(isUpdate ? "修改成功" : "新增成功");
        dialog.visible = false;
        loadList();
      })
      .catch((error) => {
        console.error(error);
      })
      .finally(() => {
        submitLoading.value = false;
      });
  });
}

/** 为申请生成正式注册码 */
function handleGenCode(row: RegistrationPageVO) {
  if (!row.id) return;
  ElMessageBox.confirm(
    `确认为「${row.applyName}（${row.company}）」生成正式注册码吗？生成后申请状态将置为已处理。`,
    "提示",
    { confirmButtonText: "确定", cancelButtonText: "取消", type: "info" }
  )
    .then(() => {
      RegistrationAPI.getRegCode(row as RegistrationForm).then((data) => {
        ElMessageBox.alert(`注册码：${data?.regCode || "见列表"}`, "生成成功", {
          confirmButtonText: "复制并关闭",
          callback: () => {
            if (data?.regCode) {
              navigator.clipboard?.writeText(data.regCode);
              ElMessage.success("注册码已复制");
            }
          },
        });
        loadList();
      });
    })
    .catch(() => {});
}

/** 生成临时多有效期注册码 */
function handleGenTempCode() {
  tempCodeLoading.value = true;
  RegistrationAPI.genTempRegCode(tempCodeDialog.applyName, tempCodeDialog.company)
    .then((data) => {
      tempCodeResult.value = data;
    })
    .catch((error) => {
      console.error(error);
    })
    .finally(() => {
      tempCodeLoading.value = false;
    });
}

function handleDelete(row: RegistrationPageVO) {
  if (!row.id) return;
  ElMessageBox.confirm(`确认删除「${row.applyName}（${row.company}）」的申请吗？`, "警告", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      RegistrationAPI.deleteByIds([row.id!]).then(() => {
        ElMessage.success("删除成功");
        loadList();
      });
    })
    .catch(() => {});
}

onMounted(() => {
  loadList();
});
</script>

<style lang="scss" scoped>
.app-container {
  padding: 16px;
}

.muted {
  color: var(--el-text-color-placeholder);
}
</style>
