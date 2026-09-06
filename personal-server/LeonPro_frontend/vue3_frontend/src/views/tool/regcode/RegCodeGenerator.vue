<template>
  <div class="regcode-generator">
    <el-empty v-if="!loadingConfigs && companies.length === 0" description="暂无可用注册码配置" />

    <el-form v-else label-width="88px" @submit.prevent>
      <el-form-item v-if="quota && !quota.unlimited" label="剩余次数">
        <el-tag type="warning">{{ quota.remaining ?? 0 }} / {{ quota.generateLimit ?? 0 }}</el-tag>
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12" :xs="24">
          <el-form-item label="公司">
            <el-select
              v-model="companyName"
              placeholder="选择公司"
              class="w-full"
              :loading="loadingConfigs"
              @change="onCompanyChange"
            >
              <el-option v-for="item in companies" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12" :xs="24">
          <el-form-item label="名称">
            <el-select v-model="configId" placeholder="选择名称" class="w-full" @change="resetResult">
              <el-option
                v-for="item in currentConfigs"
                :key="item.id"
                :label="item.name"
                :value="item.id!"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="注册码">
        <el-input
          v-model="regCode"
          maxlength="6"
          placeholder="输入 6 位注册码"
          clearable
          @keyup.enter="handleGenerate"
        />
      </el-form-item>

      <el-form-item v-for="field in visibleFields" :key="field" :label="VALIDITY_LABELS[field]">
        <div class="result-row">
          <span class="result-value">{{ result[field] || "-" }}</span>
          <el-button
            v-if="isGenerated && result[field]"
            type="primary"
            link
            size="small"
            @click="copyText(result[field]!)"
          >
            复制
          </el-button>
        </div>
      </el-form-item>

      <el-form-item v-if="hiddenFields.length">
        <el-button type="primary" link @click="expanded = !expanded">
          {{ expanded ? "收起 ▲" : "展开更多 ▼" }}
        </el-button>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="loading" @click="handleGenerate">生成</el-button>
        <el-button @click="resetAll">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import RegistrationAPI, { type TempRegCodeVO } from "@/api/registration";
import RegCodeConfigAPI, { type RegCodeConfigVO } from "@/api/tool/regcode-config";
import RegCodeUserAPI, { type RegCodeQuotaVO } from "@/api/tool/regcode-user";
import { useUserStore } from "@/store/modules/user";
import {
  ALL_VALIDITY_FIELDS,
  DEFAULT_VISIBLE_FIELDS,
  VALIDITY_LABELS,
  type ValidityKey,
} from "./config";

const userStore = useUserStore();

const configs = ref<RegCodeConfigVO[]>([]);
const quota = ref<RegCodeQuotaVO | null>(null);
const loadingConfigs = ref(false);
const companyName = ref("");
const configId = ref("");
const regCode = ref("");
const loading = ref(false);
const expanded = ref(false);
const isGenerated = ref(false);

const emptyResult = (): TempRegCodeVO => ({
  oneMonthValid: "OneMonth",
  twoMonthValid: "TwoMonth",
  fourMonthValid: "FourMonth",
  sixMonthValid: "SixMonth",
  thirteenMonthValid: "ThirteenMonth",
  longTimeValid: "Forever",
});

const result = reactive<TempRegCodeVO>(emptyResult());

const companies = computed(() => {
  const names: string[] = [];
  configs.value.forEach((item) => {
    if (item.company && !names.includes(item.company)) {
      names.push(item.company);
    }
  });
  return names;
});

const currentConfigs = computed(() =>
  configs.value.filter((item) => item.company === companyName.value)
);

const currentConfig = computed(
  () => currentConfigs.value.find((item) => item.id === configId.value) || currentConfigs.value[0]
);

const allFields = computed<ValidityKey[]>(() => ALL_VALIDITY_FIELDS);
const visibleFields = computed(() =>
  expanded.value ? allFields.value : DEFAULT_VISIBLE_FIELDS
);
const hiddenFields = computed(() =>
  expanded.value ? [] : allFields.value.filter((field) => !DEFAULT_VISIBLE_FIELDS.includes(field))
);

function applyDefaultSelection() {
  companyName.value = companies.value[0] || "";
  configId.value = currentConfigs.value[0]?.id || "";
}

function onCompanyChange() {
  configId.value = currentConfigs.value[0]?.id || "";
  expanded.value = false;
  resetResult();
}

function resetResult() {
  isGenerated.value = false;
  Object.assign(result, emptyResult());
}

function resetAll() {
  regCode.value = "";
  expanded.value = false;
  resetResult();
}

function copyText(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success("已复制");
  });
}

function loadQuota() {
  RegCodeUserAPI.myQuota()
    .then((data) => {
      quota.value = data || null;
    })
    .catch((error) => {
      console.error(error);
    });
}

function loadConfigs() {
  loadingConfigs.value = true;
  RegCodeConfigAPI.list()
    .then((data) => {
      configs.value = data || [];
      applyDefaultSelection();
    })
    .catch((error) => {
      console.error(error);
    })
    .finally(() => {
      loadingConfigs.value = false;
    });
}

function handleGenerate() {
  if (!currentConfig.value?.id) {
    ElMessage.warning("请选择名称");
    return;
  }
  if (!regCode.value || regCode.value.length !== 6) {
    ElMessage.warning("注册码长度必须为 6 位");
    return;
  }

  loading.value = true;
  RegistrationAPI.genTempRegCode({
    regCode: regCode.value,
    configId: currentConfig.value.id,
    company: currentConfig.value.company,
    applyName: currentConfig.value.name,
    applyId: userStore.userInfo.id,
  })
    .then((data) => {
      Object.assign(result, data);
      isGenerated.value = true;
      ElMessage.success("生成成功");
      loadQuota();
    })
    .catch((error) => {
      console.error(error);
    })
    .finally(() => {
      loading.value = false;
    });
}

onMounted(() => {
  loadQuota();
  loadConfigs();
});

onActivated(() => {
  loadQuota();
  loadConfigs();
});
</script>

<style lang="scss" scoped>
.regcode-generator {
  max-width: 640px;
}

.w-full {
  width: 100%;
}

.result-row {
  display: flex;
  align-items: center;
  min-height: 32px;
  gap: 8px;
}

.result-value {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 14px;
  color: var(--el-color-primary);
  word-break: break-all;
}
</style>
