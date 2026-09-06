<template>
  <div class="app-container">
    <el-card shadow="never" class="mb-4">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="用户名">
          <el-input
            v-model="queryParams.username"
            placeholder="用户名"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="所属父用户">
          <el-select
            v-model="queryParams.parentId"
            placeholder="全部父用户"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="item in parentOptions"
              :key="item.id"
              :label="parentLabel(item)"
              :value="item.id!"
            />
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
          <span>注册码用户</span>
          <el-button type="primary" @click="openDialog()">
            <el-icon class="mr-1"><Plus /></el-icon>新增用户
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column label="所属父用户" width="160">
          <template #default="{ row }">
            {{ row.parentNickname || row.parentUsername || "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" width="120">
          <template #default="{ row }">{{ row.nickname || "-" }}</template>
        </el-table-column>
        <el-table-column label="可用配置" min-width="220">
          <template #default="{ row }">
            <el-tag
              v-for="label in row.configLabels || []"
              :key="label"
              size="small"
              class="mr-1 mb-1"
            >
              {{ label }}
            </el-tag>
            <span v-if="!row.configLabels?.length">-</span>
          </template>
        </el-table-column>
        <el-table-column label="生成次数" width="140" align="center">
          <template #default="{ row }">
            {{ row.generateUsed || 0 }} / {{ row.generateLimit || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="剩余" width="80" align="center">
          <template #default="{ row }">{{ row.remaining ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" align="center" />
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
      width="560px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
        <el-form-item label="所属父用户" prop="parentId">
          <el-select
            v-model="formData.parentId"
            placeholder="选择主用户"
            filterable
            class="w-full"
          >
            <el-option
              v-for="item in parentOptions"
              :key="item.id"
              :label="parentLabel(item)"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!formData.id" label="用户名" prop="username">
          <el-input v-model="formData.username" placeholder="3-20 个字符，用于客户登录" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="formData.password"
            type="password"
            show-password
            :placeholder="formData.id ? '留空表示不修改密码' : '至少 6 位'"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="formData.nickname" placeholder="昵称" />
        </el-form-item>
        <el-form-item label="可用配置" prop="configIds">
          <el-select
            v-model="formData.configIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="仅可生成这些注册码"
            class="w-full"
          >
            <el-option
              v-for="item in configOptions"
              :key="item.id"
              :label="`${item.company} / ${item.name}`"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="可生成次数" prop="generateLimit">
          <el-input-number v-model="formData.generateLimit" :min="1" :max="99999" />
        </el-form-item>
        <el-form-item v-if="formData.id" label="已用次数" prop="generateUsed">
          <el-input-number v-model="formData.generateUsed" :min="0" :max="99999" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="可选" />
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
import RegCodeUserAPI, {
  type RegCodeUserForm,
  type RegCodeUserVO,
} from "@/api/tool/regcode-user";
import RegCodeConfigAPI, { type RegCodeConfigVO } from "@/api/tool/regcode-config";
import UserAPI, { type UserPageVO } from "@/api/system/user";

defineOptions({
  name: "RegCodeUser",
  inheritAttrs: false,
});

const loading = ref(false);
const submitLoading = ref(false);
const tableData = ref<RegCodeUserVO[]>([]);
const total = ref(0);
const configOptions = ref<RegCodeConfigVO[]>([]);
const parentOptions = ref<UserPageVO[]>([]);

const queryParams = reactive({
  current: 1,
  size: 10,
  username: "",
  parentId: "",
});

const dialog = reactive({
  visible: false,
  title: "",
});

const formRef = ref();
const formData = reactive<RegCodeUserForm>(emptyForm());

const rules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 20, message: "用户名长度须为 3-20 个字符", trigger: "blur" },
  ],
  password: [
    {
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        if (!formData.id && !value) {
          callback(new Error("请输入密码"));
          return;
        }
        if (value && value.length < 6) {
          callback(new Error("密码长度不能少于6位"));
          return;
        }
        callback();
      },
      trigger: "blur",
    },
  ],
  parentId: [{ required: true, message: "请选择所属父用户", trigger: "change" }],
  configIds: [{ required: true, type: "array", min: 1, message: "请选择可用配置", trigger: "change" }],
  generateLimit: [{ required: true, message: "请设置可生成次数", trigger: "change" }],
};

function emptyForm(): RegCodeUserForm {
  return {
    parentId: "",
    username: "",
    password: "",
    nickname: "",
    generateLimit: 10,
    generateUsed: 0,
    remark: "",
    configIds: [],
  };
}

function parentLabel(item: UserPageVO) {
  if (item.nickname && item.username && item.nickname !== item.username) {
    return `${item.nickname}（${item.username}）`;
  }
  return item.nickname || item.username || item.id || "";
}

function loadParents() {
  UserAPI.getPage({ current: 1, size: 999 })
    .then((data) => {
      parentOptions.value = data.records || [];
    })
    .catch((error) => {
      console.error(error);
    });
}

function loadConfigs() {
  RegCodeConfigAPI.list()
    .then((data) => {
      configOptions.value = data || [];
    })
    .catch((error) => {
      console.error(error);
    });
}

function loadData() {
  loading.value = true;
  RegCodeUserAPI.getPage(queryParams)
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
  queryParams.username = "";
  queryParams.parentId = "";
  handleQuery();
}

function openDialog(row?: RegCodeUserVO) {
  if (row) {
    dialog.title = "编辑注册码用户";
    Object.assign(formData, {
      id: row.id,
      userId: row.userId,
      parentId: row.parentId,
      username: row.username,
      nickname: row.nickname,
      email: row.email,
      roleId: row.roleId,
      generateLimit: row.generateLimit ?? 1,
      generateUsed: row.generateUsed ?? 0,
      remark: row.remark,
      configIds: [...(row.configIds || [])],
      password: "",
    });
  } else {
    dialog.title = "新增注册码用户";
  }
  dialog.visible = true;
}

function resetForm() {
  formRef.value?.resetFields?.();
  Object.assign(formData, emptyForm(), { id: undefined, userId: undefined });
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    submitLoading.value = true;
    const req = formData.id ? RegCodeUserAPI.update({ ...formData }) : RegCodeUserAPI.save({ ...formData });
    req
      .then((msg) => {
        ElMessage.success(typeof msg === "string" && msg ? msg : "保存成功");
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

function handleDelete(row: RegCodeUserVO) {
  if (!row.id) return;
  ElMessageBox.confirm(`确认删除注册码用户「${row.username}」吗？对应子用户账号会一并删除。`, "警告", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      RegCodeUserAPI.deleteByIds([row.id!]).then(() => {
        ElMessage.success("删除成功");
        loadData();
      });
    })
    .catch(() => {});
}

onMounted(() => {
  loadParents();
  loadConfigs();
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
