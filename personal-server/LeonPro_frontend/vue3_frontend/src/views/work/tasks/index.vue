<template>
  <div class="app-container">
    <el-card shadow="never" class="mb-4">
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="任务名称">
          <el-input
            v-model="queryParams.taskName"
            placeholder="任务名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="任务状态">
          <el-select v-model="queryParams.taskStatus" placeholder="全部" clearable class="w-36">
            <el-option label="进行中" value="进行中" />
            <el-option label="已完成" value="已完成" />
            <el-option label="已暂停" value="已暂停" />
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
          <span>任务列表</span>
          <el-button type="primary" @click="openDialog()">
            <el-icon class="mr-1"><Plus /></el-icon>新增任务
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="taskList" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="taskName" label="任务名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="taskType" label="任务类别" width="100" align="center" />
        <el-table-column prop="taskLevel" label="任务等级" width="90" align="center" />
        <el-table-column prop="taskStatus" label="任务状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.taskStatus)" size="small">
              {{ row.taskStatus || "-" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户名称" min-width="110" show-overflow-tooltip />
        <el-table-column prop="scenario" label="场景" min-width="110" show-overflow-tooltip />
        <el-table-column prop="robotType" label="机械臂型号" width="110" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="160" align="center" />
        <el-table-column label="操作" width="160" align="center" fixed="right">
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
        @pagination="loadTasks"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.title"
      width="640px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="任务名称" prop="taskName">
              <el-input v-model="formData.taskName" placeholder="请输入任务名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务类别" prop="taskType">
              <el-input v-model="formData.taskType" placeholder="例如：焊接调试" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务等级">
              <el-select v-model="formData.taskLevel" placeholder="选择等级" clearable class="w-full">
                <el-option label="高" value="高" />
                <el-option label="中" value="中" />
                <el-option label="低" value="低" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务状态">
              <el-select v-model="formData.taskStatus" placeholder="选择状态" clearable class="w-full">
                <el-option label="进行中" value="进行中" />
                <el-option label="已完成" value="已完成" />
                <el-option label="已暂停" value="已暂停" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称">
              <el-input v-model="formData.customerName" placeholder="客户名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户地址">
              <el-input v-model="formData.customerPlace" placeholder="客户地址" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属行业">
              <el-input v-model="formData.industry" placeholder="所属行业" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="应用场景">
              <el-input v-model="formData.scenario" placeholder="应用场景" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="机械臂型号">
              <el-input v-model="formData.robotType" placeholder="如 EC66 / i5" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="机械臂数量">
              <el-input v-model="formData.robotNum" placeholder="机械臂数量" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="任务描述">
              <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="任务描述" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="formData.remarks" type="textarea" :rows="2" placeholder="备注（异常情况信息）" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import TaskAPI, { type TaskPageVO, type TaskForm } from "@/api/tasks";
import { useUserStore } from "@/store/modules/user";

defineOptions({
  name: "Tasks",
  inheritAttrs: false,
});

const userStore = useUserStore();

const loading = ref(false);
const submitLoading = ref(false);
const taskList = ref<TaskPageVO[]>([]);
const total = ref(0);

const queryParams = reactive({
  current: 1,
  size: 10,
  taskName: "",
  taskStatus: "",
});

const dialog = reactive({
  visible: false,
  title: "",
});

const formRef = ref();

const formData = reactive<TaskForm>({});

const rules = {
  taskName: [{ required: true, message: "请输入任务名称", trigger: "blur" }],
  taskType: [{ required: true, message: "请输入任务类别", trigger: "blur" }],
};

function statusTagType(status?: string) {
  if (status === "已完成") return "success";
  if (status === "已暂停") return "info";
  return "primary";
}

/** 加载任务列表 */
function loadTasks() {
  loading.value = true;
  TaskAPI.getPage({
    ...queryParams,
    // 后端要求必传 publisherId，取当前登录用户 ID
    publisherId: userStore.userInfo.id,
  })
    .then((data) => {
      taskList.value = data.records || [];
      total.value = data.total || 0;
    })
    .catch((error) => {
      console.error("加载任务失败", error);
    })
    .finally(() => {
      loading.value = false;
    });
}

function handleQuery() {
  queryParams.current = 1;
  loadTasks();
}

function resetQuery() {
  queryParams.taskName = "";
  queryParams.taskStatus = "";
  handleQuery();
}

function openDialog(row?: TaskPageVO) {
  if (row) {
    dialog.title = "编辑任务";
    Object.assign(formData, row);
  } else {
    dialog.title = "新增任务";
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
    const payload = { ...formData, publisherId: userStore.userInfo.id };
    const request = isUpdate ? TaskAPI.update(payload) : TaskAPI.add(payload);
    request
      .then(() => {
        ElMessage.success(isUpdate ? "修改成功" : "新增成功");
        dialog.visible = false;
        loadTasks();
      })
      .catch((error) => {
        console.error(error);
      })
      .finally(() => {
        submitLoading.value = false;
      });
  });
}

function handleDelete(row: TaskPageVO) {
  if (!row.id) return;
  ElMessageBox.confirm(`确认删除任务「${row.taskName}」吗？`, "警告", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      TaskAPI.deleteByIds([row.id!]).then(() => {
        ElMessage.success("删除成功");
        loadTasks();
      });
    })
    .catch(() => {});
}

onMounted(() => {
  loadTasks();
});
</script>

<style lang="scss" scoped>
.app-container {
  padding: 16px;
}
</style>
