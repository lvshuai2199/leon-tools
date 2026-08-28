/**
 * 前端增删改查页面模板（搜索 + 表格 + 弹窗）。
 *
 * 复制本文件到目标模块后：
 * 1. 改 defineOptions.name、API import、表格列、表单字段
 * 2. 在路由配置 / 菜单里注册页面
 * 3. 已落地示例：views/tool/regcode-config/index.vue
 */
<template>
  <div class="app-container">
    <el-card shadow="never" class="mb-4">
      <el-form :inline="true" :model="queryParams">
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
          <span>列表示例</span>
          <el-button type="primary" @click="openDialog()">
            <el-icon class="mr-1"><Plus /></el-icon>新增
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="name" label="名称" min-width="160" />
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
      width="480px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="88px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="名称" />
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
import CrudAPI, { type CrudPageVO, type CrudForm } from "@/api/_template/crud";

defineOptions({
  name: "CrudTemplate",
  inheritAttrs: false,
});

const loading = ref(false);
const submitLoading = ref(false);
const tableData = ref<CrudPageVO[]>([]);
const total = ref(0);

const queryParams = reactive({
  current: 1,
  size: 10,
  name: "",
});

const dialog = reactive({
  visible: false,
  title: "",
});

const formRef = ref();
const formData = reactive<CrudForm>({
  name: "",
});

const rules = {
  name: [{ required: true, message: "请输入名称", trigger: "blur" }],
};

function loadData() {
  loading.value = true;
  CrudAPI.getPage(queryParams)
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
  queryParams.name = "";
  handleQuery();
}

function openDialog(row?: CrudPageVO) {
  if (row) {
    dialog.title = "编辑";
    Object.assign(formData, { id: row.id, name: row.name });
  } else {
    dialog.title = "新增";
  }
  dialog.visible = true;
}

function resetForm() {
  formRef.value?.resetFields?.();
  formData.id = undefined;
  formData.name = "";
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    submitLoading.value = true;
    const req = formData.id ? CrudAPI.update({ ...formData }) : CrudAPI.add({ ...formData });
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

function handleDelete(row: CrudPageVO) {
  if (!row.id) return;
  ElMessageBox.confirm(`确认删除「${row.name}」吗？`, "警告", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      CrudAPI.deleteByIds([row.id!]).then(() => {
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
</style>
