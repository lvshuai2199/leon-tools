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
        <el-form-item v-if="isRoot" label="所属父用户">
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
            <el-icon class="mr-1"><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="resetQuery">
            <el-icon class="mr-1"><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="flex-x-between">
          <span>注册码客户</span>
          <el-button type="primary" @click="openDialog()">
            <el-icon class="mr-1"><Plus /></el-icon>
            新增客户
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" row-key="id" border>
        <el-table-column type="expand" width="40">
          <template #default="{ row }">
            <SubUserPanel :customer-id="customerKey(row)" />
          </template>
        </el-table-column>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column v-if="isRoot" label="所属父用户" width="160">
          <template #default="{ row }">
            {{ row.parentNickname || row.parentUsername || "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" width="120">
          <template #default="{ row }">{{ row.nickname || "-" }}</template>
        </el-table-column>
        <el-table-column label="各配置次数（已用 / 上限）" min-width="240">
          <template #default="{ row }">
            <el-tag
              v-for="q in rowQuotas(row)"
              :key="q.configId"
              :type="(q.remaining ?? 0) > 0 ? 'primary' : 'info'"
              size="small"
              class="mr-1 mb-1"
            >
              {{ q.configName }} {{ q.used ?? 0 }} / {{ q.allocated ?? 0 }}
            </el-tag>
            <span v-if="!rowQuotas(row).length">-</span>
          </template>
        </el-table-column>
        <el-table-column label="子用户" width="100" align="center">
          <template #default="{ row }">
            {{ row.subUserCount ?? 0 }} / {{ row.maxSubUsers ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'info' : 'success'" size="small">
              {{ row.status === 0 ? "停用" : "启用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="createTime"
          label="创建时间"
          width="170"
          align="center"
          :formatter="tableTimeFormatter"
        />
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
        <el-form-item v-if="isRoot" label="所属父用户" prop="parentId">
          <el-select v-model="formData.parentId" placeholder="选择主用户" filterable class="w-full">
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
              :label="configLabel(item)"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="formData.configIds?.length" label="各配置次数">
          <div class="quota-list">
            <div v-for="cid in formData.configIds" :key="cid" class="quota-list__row">
              <span class="quota-list__name">{{ configName(cid) }}</span>
              <el-input-number
                v-model="quotaCounts[cid]"
                :min="usedOf(cid)"
                :max="99999"
                :step="1"
                step-strictly
                size="small"
                controls-position="right"
              />
              <span v-if="usedOf(cid)" class="quota-list__hint">已用 {{ usedOf(cid) }}</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="可建子用户数" prop="maxSubUsers">
          <el-input-number v-model="formData.maxSubUsers" :min="0" :max="99" />
          <span class="form-hint">0 表示不能建子用户；调小后已有子用户保留，只是不能再新建</span>
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
import { tableTimeFormatter } from "@/utils";
import RegCodeUserAPI, {
  customerKey,
  type RegCodeQuotaItem,
  type RegCodeUserForm,
  type RegCodeUserVO,
} from "@/api/tool/regcode-user";
import RegCodeConfigAPI, { type RegCodeConfigVO } from "@/api/tool/regcode-config";
import UserAPI, { type UserPageVO } from "@/api/system/user";
import { useUserStore } from "@/store/modules/user";
import { isRootRole } from "@/utils/role";
import SubUserPanel from "./components/SubUserPanel.vue";

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
const userStore = useUserStore();
const isRoot = computed(() =>
  isRootRole({ id: userStore.userInfo.roleId, roleName: userStore.userInfo.roleName })
);
const myUserId = computed(() => String(userStore.userInfo.id || userStore.userInfo.userId || ""));

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
/** 编辑弹窗里各配置的次数上限，按 configId 存 */
const quotaCounts = reactive<Record<string, number>>({});
/** 正在编辑的客户各配置已用次数（上限不能低于它） */
const usedCounts = ref<Record<string, number>>({});
const DEFAULT_QUOTA = 10;

const rules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 20, message: "用户名长度须为 3-20 个字符", trigger: "blur" },
  ],
  password: [
    {
      validator: (_rule: unknown, value: string, callback: (_err?: Error) => void) => {
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
  parentId: [
    {
      validator: (_rule: unknown, value: string, callback: (_err?: Error) => void) => {
        if (isRoot.value && !value) {
          callback(new Error("请选择所属父用户"));
          return;
        }
        callback();
      },
      trigger: "change",
    },
  ],
  configIds: [
    { required: true, type: "array", min: 1, message: "请选择可用配置", trigger: "change" },
  ],
};

/** 选了新配置时给默认次数 */
watch(
  () => formData.configIds,
  (ids) => {
    (ids || []).forEach((id) => {
      if (quotaCounts[id] === undefined) quotaCounts[id] = Math.max(DEFAULT_QUOTA, usedOf(id));
    });
  },
  { deep: true }
);

function emptyForm(): RegCodeUserForm {
  return {
    parentId: isRoot.value ? "" : myUserId.value,
    username: "",
    password: "",
    nickname: "",
    remark: "",
    configIds: [],
    maxSubUsers: 0,
  };
}

function clearQuotaCounts() {
  Object.keys(quotaCounts).forEach((k) => delete quotaCounts[k]);
  usedCounts.value = {};
}

/** 列表里的次数明细；老数据没有 quotas 时按可用配置 + 合计显示 */
function rowQuotas(row: RegCodeUserVO): RegCodeQuotaItem[] {
  if (row.quotas?.length) return row.quotas;
  return (row.configLabels || []).map((label, i) => ({
    configId: row.configIds?.[i] || label,
    configName: label,
  }));
}

function configLabel(item: RegCodeConfigVO) {
  return [item.company, item.name].filter(Boolean).join(" / ");
}

function configName(id: string) {
  const item = configOptions.value.find((c) => String(c.id) === String(id));
  return item ? configLabel(item) : id;
}

function usedOf(id: string) {
  return usedCounts.value[id] ?? 0;
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

function refreshOptions() {
  if (isRoot.value) {
    loadParents();
  }
  loadConfigs();
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
  refreshOptions();
  if (row) {
    dialog.title = "编辑注册码客户";
    Object.assign(formData, {
      id: row.id,
      userId: row.userId,
      parentId: isRoot.value ? row.parentId || "" : myUserId.value,
      username: row.username,
      nickname: row.nickname,
      email: row.email,
      roleId: row.roleId,
      remark: row.remark,
      maxSubUsers: row.maxSubUsers ?? 0,
      password: "",
    });
    clearQuotaCounts();
    const quotas = row.quotas || [];
    const used: Record<string, number> = {};
    quotas.forEach((q) => {
      const id = String(q.configId ?? "");
      if (!id) return;
      used[id] = q.used ?? 0;
      quotaCounts[id] = q.allocated ?? 0;
    });
    usedCounts.value = used;
    formData.configIds = quotas.length
      ? quotas.map((q) => String(q.configId))
      : [...(row.configIds || [])];
  } else {
    dialog.title = "新增注册码客户";
    clearQuotaCounts();
    Object.assign(formData, emptyForm());
  }
  dialog.visible = true;
}

function resetForm() {
  formRef.value?.resetFields?.();
  Object.assign(formData, emptyForm(), { id: undefined, userId: undefined });
  clearQuotaCounts();
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    submitLoading.value = true;
    const configIds = [...(formData.configIds || [])];
    const payload: RegCodeUserForm = {
      ...formData,
      // 编辑时 id 传 userId（后端两种都认）
      id: formData.id ? customerKey(formData) : undefined,
      parentId: isRoot.value ? formData.parentId : myUserId.value,
      configIds,
      quotas: configIds.map((id) => ({
        configId: id,
        count: Math.max(quotaCounts[id] ?? 0, usedOf(id)),
      })),
      maxSubUsers: formData.maxSubUsers ?? 0,
    };
    const req = formData.id ? RegCodeUserAPI.update(payload) : RegCodeUserAPI.save(payload);
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
  const key = customerKey(row);
  if (!key) return;
  ElMessageBox.confirm(
    `确认删除客户「${row.username}」吗？它创建的子用户会保留账号，但会被停用，没用完的次数作废。`,
    "警告",
    {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    }
  )
    .then(() => RegCodeUserAPI.deleteByIds([key]))
    .then((res) => {
      const retired = res?.retiredSubUsers ?? 0;
      const voided = res?.voidedTotal ?? 0;
      ElMessage.success(
        retired || voided ? `已删除，已停用 ${retired} 个子用户，作废 ${voided} 次` : "删除成功"
      );
      loadData();
    })
    .catch(() => {});
}

onMounted(() => {
  refreshOptions();
  loadData();
});

onActivated(() => {
  refreshOptions();
});
</script>

<style lang="scss" scoped>
.app-container {
  padding: 16px;
}

.w-full {
  width: 100%;
}

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

.quota-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;

  &__row {
    display: flex;
    gap: 12px;
    align-items: center;
  }

  &__name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
