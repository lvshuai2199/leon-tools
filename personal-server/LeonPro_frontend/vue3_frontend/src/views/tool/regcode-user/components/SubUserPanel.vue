<template>
  <div v-loading="loading" class="sub-user-panel">
    <div class="sub-user-panel__head">
      <span>子用户</span>
      <span class="sub-user-panel__count">
        启用中 {{ list.createdCount ?? 0 }} / 最多 {{ list.maxSubUsers ?? 0 }} 个
      </span>
      <el-button link type="primary" size="small" @click="load">刷新</el-button>
    </div>
    <el-table v-if="list.items?.length" :data="list.items" size="small" border>
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column label="昵称" width="120">
        <template #default="{ row }">{{ row.nickname || "-" }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'info' : 'success'" size="small">
            {{ row.status === 0 ? "停用" : "启用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已用 / 分到" width="110" align="center">
        <template #default="{ row }">
          {{ row.usedTotal ?? 0 }} / {{ row.allocatedTotal ?? 0 }}
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" align="center" />
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openQuota(row)">调整次数</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-else-if="!loading" class="sub-user-panel__empty">这个客户还没有创建子用户</div>

    <SubUserQuotaDialog v-model="quotaVisible" :sub-user="current" @saved="load" />
  </div>
</template>

<script setup lang="ts">
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
  padding: 8px 16px 12px 48px;

  &__head {
    display: flex;
    gap: 12px;
    align-items: center;
    margin-bottom: 8px;
    font-weight: 500;
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
