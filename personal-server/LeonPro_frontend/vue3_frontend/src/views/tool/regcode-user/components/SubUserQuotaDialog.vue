<template>
  <el-dialog
    :model-value="modelValue"
    :title="`调整子用户次数：${subUser?.nickname || subUser?.username || ''}`"
    width="600px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @open="load"
  >
    <el-alert type="info" :closable="false" show-icon class="quota-note">
      加的次数由管理员直接给子用户，不从客户那里扣；收回的只能是未用次数，收回后作废，不退给客户。
    </el-alert>

    <el-table v-loading="loading" :data="rows" border size="small">
      <el-table-column prop="configName" label="配置" min-width="160" />
      <el-table-column label="已用 / 上限" width="110" align="center">
        <template #default="{ row }">{{ row.used }} / {{ row.allocated }}</template>
      </el-table-column>
      <el-table-column label="剩余" width="70" align="center" prop="remaining" />
      <el-table-column label="调整（正数加，负数收回）" width="200" align="center">
        <template #default="{ row }">
          <el-input-number
            v-model="row.delta"
            :min="-row.remaining"
            :max="99999"
            :step="1"
            step-strictly
            size="small"
            controls-position="right"
          />
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无可调整的配置" :image-size="60" />

    <template #footer>
      <span class="quota-summary">{{ summary }}</span>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!changed.length" @click="submit">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import RegCodeUserAPI, { type SubUserQuotaVO, type SubUserVO } from "@/api/tool/regcode-user";

interface QuotaRow {
  configId: string;
  configName: string;
  allocated: number;
  used: number;
  remaining: number;
  delta: number;
}

const props = defineProps<{ modelValue: boolean; subUser: SubUserVO | null }>();
const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  saved: [];
}>();

const loading = ref(false);
const submitting = ref(false);
const rows = ref<QuotaRow[]>([]);

const changed = computed(() => rows.value.filter((r) => r.delta));
const summary = computed(() => {
  const add = changed.value.filter((r) => r.delta > 0).reduce((s, r) => s + r.delta, 0);
  const take = changed.value.filter((r) => r.delta < 0).reduce((s, r) => s - r.delta, 0);
  const parts: string[] = [];
  if (add) parts.push(`加 ${add} 次`);
  if (take) parts.push(`收回并作废 ${take} 次`);
  return parts.join("，");
});

/** 子用户已有的配置 + 客户自己有的配置（子用户还没有的按 0 次显示，可以直接加） */
function toRows(data?: SubUserQuotaVO) {
  const map = new Map<string, QuotaRow>();
  (data?.items || []).forEach((it) => {
    const id = String(it.configId ?? "");
    if (!id) return;
    map.set(id, {
      configId: id,
      configName: it.configName || id,
      allocated: it.allocated ?? 0,
      used: it.used ?? 0,
      remaining: it.remaining ?? 0,
      delta: 0,
    });
  });
  (data?.creatorRemaining || []).forEach((it) => {
    const id = String(it.configId ?? "");
    if (!id || map.has(id)) return;
    map.set(id, {
      configId: id,
      configName: it.configName || id,
      allocated: 0,
      used: 0,
      remaining: 0,
      delta: 0,
    });
  });
  return [...map.values()];
}

async function load() {
  const id = props.subUser?.id;
  rows.value = [];
  if (!id) return;
  loading.value = true;
  try {
    rows.value = toRows(await RegCodeUserAPI.getSubUserQuota(String(id)));
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
}

async function submit() {
  const id = props.subUser?.id;
  if (!id || !changed.value.length) return;
  submitting.value = true;
  try {
    const data = await RegCodeUserAPI.adjustSubUserQuota(
      String(id),
      changed.value.map((r) => ({ configId: r.configId, delta: r.delta }))
    );
    rows.value = toRows(data);
    ElMessage.success("次数已调整");
    emit("saved");
    emit("update:modelValue", false);
  } catch (error) {
    console.error(error);
  } finally {
    submitting.value = false;
  }
}
</script>

<style lang="scss" scoped>
.quota-note {
  margin-bottom: 12px;
}

.quota-summary {
  margin-right: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
