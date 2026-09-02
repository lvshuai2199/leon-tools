<template>
  <div class="app-container">
    <el-card shadow="never" class="mb-4">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="操作人">
          <el-input
            v-model="queryParams.operatorName"
            placeholder="用户名 / 未知人员"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="模块">
          <el-select v-model="queryParams.module" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAIL" />
            <el-option label="异常" value="ERROR" />
          </el-select>
        </el-form-item>
        <el-form-item label="路径">
          <el-input
            v-model="queryParams.requestUri"
            placeholder="请求路径"
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
        <span class="header-hint">记录写操作、登录、业务失败与系统异常，便于回溯与崩溃排查</span>
      </template>

      <el-table v-loading="loading" :data="logList" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="createTime" label="时间" width="170" align="center" />
        <el-table-column label="操作人" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.operatorName || "未知人员" }}
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="110" align="center" />
        <el-table-column prop="action" label="动作" min-width="200" show-overflow-tooltip />
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'SUCCESS'" type="success">成功</el-tag>
            <el-tag v-else-if="row.status === 'FAIL'" type="warning">失败</el-tag>
            <el-tag v-else-if="row.status === 'ERROR'" type="danger">异常</el-tag>
            <span v-else>{{ row.status || "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="resultMsg" label="摘要" min-width="140" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="130" align="center" />
        <el-table-column prop="costMs" label="耗时" width="80" align="center">
          <template #default="{ row }">
            {{ row.costMs != null ? row.costMs + "ms" : "-" }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
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

    <el-drawer v-model="detail.visible" title="操作详情" size="520px" destroy-on-close>
      <el-descriptions v-if="detail.data" :column="1" border>
        <el-descriptions-item label="时间">{{ detail.data.createTime || "-" }}</el-descriptions-item>
        <el-descriptions-item label="操作人">
          {{ detail.data.operatorName || "未知人员" }}
          <span v-if="detail.data.operatorId" class="muted">（{{ detail.data.operatorId }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="模块">{{ detail.data.module || "-" }}</el-descriptions-item>
        <el-descriptions-item label="动作">{{ detail.data.action || "-" }}</el-descriptions-item>
        <el-descriptions-item label="方法">{{ detail.data.requestMethod || "-" }}</el-descriptions-item>
        <el-descriptions-item label="路径">{{ detail.data.requestUri || "-" }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.data.ip || "-" }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ statusText(detail.data.status) }}</el-descriptions-item>
        <el-descriptions-item label="摘要">{{ detail.data.resultMsg || "-" }}</el-descriptions-item>
        <el-descriptions-item label="耗时">
          {{ detail.data.costMs != null ? detail.data.costMs + " ms" : "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="User-Agent">{{ detail.data.userAgent || "-" }}</el-descriptions-item>
      </el-descriptions>
      <div class="block-title">请求参数</div>
      <pre class="payload">{{ detail.data?.requestParams || "-" }}</pre>
      <div class="block-title">失败 / 异常信息</div>
      <pre class="payload">{{ detail.data?.errorMsg || "-" }}</pre>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import OperationLogAPI, { type OperationLogVO } from "@/api/system/oplog";

defineOptions({
  name: "Oplog",
  inheritAttrs: false,
});

const loading = ref(false);
const logList = ref<OperationLogVO[]>([]);
const total = ref(0);

const modules = [
  "认证",
  "用户管理",
  "角色管理",
  "路由配置",
  "任务管理",
  "注册码记录",
  "注册码配置",
  "思维导图",
  "系统数据",
  "外部接口",
  "其他",
];

const queryParams = reactive({
  current: 1,
  size: 10,
  operatorName: "",
  module: "",
  status: "",
  requestUri: "",
});

const detail = reactive<{ visible: boolean; data: OperationLogVO | null }>({
  visible: false,
  data: null,
});

function statusText(status?: string) {
  if (status === "SUCCESS") return "成功";
  if (status === "FAIL") return "失败";
  if (status === "ERROR") return "异常";
  return status || "-";
}

function loadList() {
  loading.value = true;
  OperationLogAPI.getPage(queryParams)
    .then((data) => {
      logList.value = data.records || [];
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
  queryParams.operatorName = "";
  queryParams.module = "";
  queryParams.status = "";
  queryParams.requestUri = "";
  handleQuery();
}

function openDetail(row: OperationLogVO) {
  if (!row.id) {
    detail.data = row;
    detail.visible = true;
    return;
  }
  OperationLogAPI.getById(row.id)
    .then((data) => {
      detail.data = data || row;
      detail.visible = true;
    })
    .catch(() => {
      detail.data = row;
      detail.visible = true;
    });
}

onMounted(() => {
  loadList();
});
</script>

<style lang="scss" scoped>
.app-container {
  padding: 16px;
}

.header-hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-weight: normal;
}

.muted {
  color: var(--el-text-color-secondary);
}

.block-title {
  margin: 16px 0 8px;
  font-weight: 600;
}

.payload {
  margin: 0;
  padding: 12px;
  max-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
}
</style>
