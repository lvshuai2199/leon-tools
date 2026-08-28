<template>
  <div class="app-container">
    <el-card shadow="never" class="mb-4">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="操作人员">
          <el-input
            v-model="queryParams.operator"
            placeholder="用户 ID / 未知人员"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
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
            v-model="queryParams.applyName"
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
        <span>操作日志</span>
      </template>

      <el-table v-loading="loading" :data="regList" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="操作人员" min-width="140" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            {{ displayOperator(row.operator) }}
          </template>
        </el-table-column>
        <el-table-column prop="company" label="公司" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.company || "-" }}
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ displayName(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="regCode" label="注册码" width="120" align="center">
          <template #default="{ row }">
            {{ row.regCode || "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="生成时间" width="200" align="center">
          <template #default="{ row }">
            {{ row.createTime || "-" }}
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
  </div>
</template>

<script setup lang="ts">
import RegistrationAPI, { type RegistrationPageVO } from "@/api/registration";

defineOptions({
  name: "Registration",
  inheritAttrs: false,
});

const loading = ref(false);
const regList = ref<RegistrationPageVO[]>([]);
const total = ref(0);

const queryParams = reactive({
  current: 1,
  size: 10,
  operator: "",
  company: "",
  applyName: "",
});

function displayOperator(operator?: string) {
  return operator && operator.trim() ? operator : "未知人员";
}

/** 新记录用配置名称；旧记录没有名称时回退到历史类型码 */
function displayName(row: RegistrationPageVO) {
  if (row.applyName && row.applyName.trim()) {
    return row.applyName;
  }
  if (row.regCodeType === 1) return "焊接专机";
  if (row.regCodeType === 2) return "码垛专机";
  if (row.regCodeType === 3) return "CNC插件";
  return "-";
}

function loadList() {
  loading.value = true;
  RegistrationAPI.getPage(queryParams)
    .then((data) => {
      regList.value = data.records || [];
      total.value = data.total || 0;
    })
    .catch((error) => {
      console.error("加载操作日志失败", error);
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
  queryParams.operator = "";
  queryParams.company = "";
  queryParams.applyName = "";
  handleQuery();
}

onMounted(() => {
  loadList();
});
</script>

<style lang="scss" scoped>
.app-container {
  padding: 16px;
}
</style>
