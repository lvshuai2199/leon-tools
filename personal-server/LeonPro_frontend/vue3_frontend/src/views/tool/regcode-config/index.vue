<template>
  <div class="app-container">
    <el-card shadow="never" class="mb-4">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="公司">
          <el-input
            v-model="queryParams.company"
            placeholder="公司"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="名称">
          <el-input
            v-model="queryParams.name"
            placeholder="名称"
            clearable
            @keyup.enter="handleQuery"
          />
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
          <span>注册码配置</span>
          <el-button type="primary" @click="openDialog()">
            <el-icon class="mr-1"><Plus /></el-icon>新增配置
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="company" label="公司" min-width="120" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="componentName" label="组件名称" min-width="120" />
        <el-table-column prop="encryptType" label="加密方式" width="110" align="center" />
        <el-table-column prop="encryptSuffix" label="加密字符后缀" min-width="140" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="180" align="center" />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
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
        @pagination="loadData"
      />
    </el-card>

    <el-dialog
      v-model="dialog.visible"
      :title="dialog.title"
      width="520px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
        <el-form-item label="公司" prop="company">
          <el-input v-model="formData.company" placeholder="如：通用、友博" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="如：焊接专机、CNC插件" />
        </el-form-item>
        <el-form-item label="组件名称" prop="componentName">
          <el-input v-model="formData.componentName" placeholder="如：weld，后续按此拆分页面" />
        </el-form-item>
        <el-form-item label="加密方式" prop="encryptType">
          <el-select v-model="formData.encryptType" placeholder="加密方式" class="w-full">
            <el-option v-for="item in ENCRYPT_TYPE_OPTIONS" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="加密字符后缀" prop="encryptSuffix">
          <el-input v-model="formData.encryptSuffix" placeholder="拼在注册码后做哈希，如 auboweld" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import RegCodeConfigAPI, {
  ENCRYPT_TYPE_OPTIONS,
  type RegCodeConfigForm,
  type RegCodeConfigVO,
} from "@/api/tool/regcode-config";

defineOptions({
  name: "RegCodeConfig",
  inheritAttrs: false,
});

const loading = ref(false);
const submitLoading = ref(false);
const tableData = ref<RegCodeConfigVO[]>([]);
const total = ref(0);

const queryParams = reactive({
  current: 1,
  size: 10,
  company: "",
  name: "",
});

const dialog = reactive({
  visible: false,
  title: "",
});

const formRef = ref();
const formData = reactive<RegCodeConfigForm>({
  company: "",
  name: "",
  componentName: "",
  encryptType: "MD5",
  encryptSuffix: "",
  sortOrder: 0,
});

const rules = {
  company: [{ required: true, message: "请输入公司", trigger: "blur" }],
  name: [{ required: true, message: "请输入名称", trigger: "blur" }],
  componentName: [{ required: true, message: "请输入组件名称", trigger: "blur" }],
  encryptType: [{ required: true, message: "请选择加密方式", trigger: "change" }],
  encryptSuffix: [{ required: true, message: "请输入加密字符后缀", trigger: "blur" }],
};

function emptyForm(): RegCodeConfigForm {
  return {
    company: "",
    name: "",
    componentName: "",
    encryptType: "MD5",
    encryptSuffix: "",
    sortOrder: 0,
  };
}

function loadData() {
  loading.value = true;
  RegCodeConfigAPI.getPage(queryParams)
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
  queryParams.company = "";
  queryParams.name = "";
  handleQuery();
}

function openDialog(row?: RegCodeConfigVO) {
  if (row) {
    dialog.title = "编辑配置";
    Object.assign(formData, {
      id: row.id,
      company: row.company,
      name: row.name,
      componentName: row.componentName,
      encryptType: row.encryptType || "MD5",
      encryptSuffix: row.encryptSuffix,
      sortOrder: row.sortOrder ?? 0,
    });
  } else {
    dialog.title = "新增配置";
  }
  dialog.visible = true;
}

function resetForm() {
  formRef.value?.resetFields?.();
  Object.assign(formData, emptyForm(), { id: undefined });
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    submitLoading.value = true;
    const req = formData.id
      ? RegCodeConfigAPI.update({ ...formData })
      : RegCodeConfigAPI.add({ ...formData });
    req
      .then(() => {
        ElMessage.success("保存成功");
        dialog.visible = false;
        loadData();
      })
      .catch((error) => {
        console.error(error);
      })
      .finally(() => {
        submitLoading.value = false;
      });
  });
}

function handleDelete(row: RegCodeConfigVO) {
  if (!row.id) return;
  ElMessageBox.confirm(`确认删除「${row.company} / ${row.name}」吗？`, "警告", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      RegCodeConfigAPI.deleteByIds([row.id!]).then(() => {
        ElMessage.success("删除成功");
        loadData();
      });
    })
    .catch(() => {});
}

onMounted(() => {
  loadData();
});
</script>

<style lang="scss" scoped>
.app-container {
  padding: 16px;
}

.w-full {
  width: 100%;
}
</style>
