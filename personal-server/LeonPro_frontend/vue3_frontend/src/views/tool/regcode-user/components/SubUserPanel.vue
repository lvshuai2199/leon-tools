<template>
  <div v-loading="loading" class="sub-user-panel">
    <div class="sub-user-panel__head">
      <span class="sub-user-panel__title">子用户</span>
      <span class="sub-user-panel__count">
        启用中 {{ list.createdCount ?? 0 }} / 最多 {{ list.maxSubUsers ?? 0 }} 个
      </span>
      <el-button link type="primary" size="small" @click="load">刷新</el-button>
    </div>
    <el-table v-if="list.items?.length" :data="list.items" class="sub-user-panel__table">
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column label="昵称" min-width="100">
        <template #default="{ row }">{{ row.nickname || "-" }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'info' : 'success'" size="small">
            {{ row.status === 0 ? "停用" : "启用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已用 / 分到" min-width="110" align="center">
        <template #default="{ row }">
          {{ row.usedTotal ?? 0 }} / {{ row.allocatedTotal ?? 0 }}
        </template>
      </el-table-column>
      <el-table-column
        prop="createTime"
        label="创建时间"
        min-width="170"
        align="center"
        :formatter="tableTimeFormatter"
      />
      <el-table-column label="操作" width="120" align="right" header-align="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openQuota(row)">调整次数</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-else-if="!loading" class="sub-user-panel__empty">这个客户还没有创建子用户</div>

    <SubUserQuotaDialog v-model="quotaVisible" :sub-user="current" @saved="load" />
  </div>
</template>

<script setup lang="ts">
import { tableTimeFormatter } from "@/utils";
import RegCodeUserAPI, { type SubUserListVO, type SubUserVO } from "@/api/tool/regcode-user";
import SubUserQuotaDialog from "./SubUserQuotaDialog.vue";

const props = defineProps<{ customerId: string }>();

const loading = ref(false);
const list = ref<SubUserListVO>({});
const quotaVisible = ref(false);
const current = ref<SubUserVO | null>(null);

async function load() {
  if (!props.customerId) return;
  loading.value = true;
  try {
    list.value = (await RegCodeUserAPI.listSubUsers(props.customerId)) || {};
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
}

function openQuota(row: SubUserVO) {
  current.value = row;
  quotaVisible.value = true;
}

onMounted(load);
</script>

<style lang="scss" scoped>
.sub-user-panel {
  min-height: 60px;

  &__head {
    display: flex;
    gap: 12px;
    align-items: center;
    margin-bottom: 8px;
  }

  &__title {
    font-size: 14px;
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  /* 子表：1px 边框 + 圆角 8，列宽用 min-width 铺满 */
  &__table {
    overflow: hidden;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;

    :deep(.el-table__inner-wrapper::before) {
      display: none;
    }

    :deep(.el-table__cell) {
      height: 40px;
      padding: 0;
    }
  }

  &__count {
    font-size: 13px;
    font-weight: normal;
    color: var(--el-text-color-secondary);
  }

  &__empty {
    padding: 8px 0;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}
</style>
