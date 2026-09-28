<script setup lang="ts">
/**
 * 公开分享页 /s/crab/:publicId（免登录）。电话由后端打码，前端原样显示。
 * 顶部用蟹单橙渐变（主题里蟹单橙只用于图标底色和这里）。
 */
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { CopyDocument } from '@element-plus/icons-vue'
import crabApi from '@/api/crab'
import { ApiError, isNotFound } from '@/api/request'
import type { CrabShipmentPublic } from '@/api/types'
import StateBlock from '@/components/StateBlock.vue'
import { copyText } from '@/utils/ui'
import CrabStatusTag from '../components/CrabStatusTag.vue'

const route = useRoute()
const loading = ref(true)
const notFound = ref(false)
const error = ref('')
const view = ref<CrabShipmentPublic | null>(null)

async function load() {
  const id = typeof route.params.publicId === 'string' ? route.params.publicId : ''
  loading.value = true
  notFound.value = false
  error.value = ''
  if (!id) {
    notFound.value = true
    loading.value = false
    return
  }
  try {
    view.value = (await crabApi.publicCrabShipment(id, { silent: true })) || null
    if (!view.value) notFound.value = true
  } catch (e) {
    if (isNotFound(e) || (e instanceof ApiError && e.effectiveStatus === 403)) notFound.value = true
    else error.value = (e as Error)?.message || '加载失败'
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="share">
    <div class="share__card">
      <p class="share__brand">出货状态</p>
      <StateBlock v-if="loading" type="loading" compact />
      <StateBlock v-else-if="notFound" type="notfound" compact title="出货单不存在或链接已失效" />
      <StateBlock v-else-if="error" type="error" compact title="加载失败" :desc="error" @retry="load" />
      <template v-else-if="view">
        <h1 class="share__name">{{ view.customerName || '出货单' }}</h1>
        <p class="share__spec">{{ view.spec || '-' }} · {{ view.quantity || 0 }} 只</p>
        <div class="share__tags">
          <CrabStatusTag field="paid" :on="view.paid" readonly size="large" />
          <CrabStatusTag field="shipped" :on="view.shipped" readonly size="large" />
        </div>
        <dl class="share__info">
          <div><dt>出货日期</dt><dd>{{ view.shipDate || '-' }}</dd></div>
          <div><dt>电话</dt><dd>{{ view.phone || '-' }}</dd></div>
          <div><dt>地址</dt><dd>{{ view.address || '未填写地址' }}</dd></div>
        </dl>
        <div class="share__track">
          <div class="share__track-main">
            <p class="share__track-label">发货单号</p>
            <p class="share__track-value" :class="{ 'is-empty': !view.trackingNo }">{{ view.trackingNo || '暂无' }}</p>
          </div>
          <el-button v-if="view.trackingNo" type="primary" plain :icon="CopyDocument" class="share__copy" @click="copyText(String(view.trackingNo), '单号已复制')">
            复制
          </el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.share {
  @include lp.mobile-vars;
  min-height: 100vh;
  min-height: 100dvh;
  padding: calc(28px + env(safe-area-inset-top)) lp.$space-4 calc(#{lp.$space-6} + env(safe-area-inset-bottom));
  background: linear-gradient(
    180deg,
    var(--lp-color-crab) 0,
    var(--lp-color-crab) 120px,
    var(--el-bg-color-page) 240px
  );
}
.share__card {
  max-width: 480px;
  margin: 0 auto;
  padding: lp.$space-5 lp.$space-4;
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
}
.share__brand {
  margin: 0;
  font-size: lp.$font-size-base;
  font-weight: lp.$font-weight-semibold;
  color: var(--lp-color-crab-text);
}
.share__name {
  margin: lp.$space-2 0 0;
  font-size: 24px;
  font-weight: lp.$font-weight-semibold;
  color: var(--el-text-color-primary);
}
.share__spec {
  margin: lp.$space-1 0 0;
  font-size: lp.$font-size-medium;
  font-weight: lp.$font-weight-medium;
  color: var(--lp-color-crab-text);
}
.share__tags {
  display: flex;
  gap: lp.$space-2;
  margin-top: lp.$space-3;
}
.share__info {
  margin: lp.$space-4 0 0;
  div {
    display: flex;
    gap: lp.$space-3;
    padding: lp.$space-2 0;
    border-top: 1px solid var(--el-border-color-lighter);
  }
  dt {
    flex: none;
    width: 64px;
    color: var(--el-text-color-secondary);
  }
  dd {
    flex: 1;
    min-width: 0;
    margin: 0;
    color: var(--el-text-color-primary);
    word-break: break-all;
  }
}
.share__track {
  display: flex;
  align-items: center;
  gap: lp.$space-3;
  margin-top: lp.$space-3;
  padding: lp.$space-3;
  border-radius: lp.$radius-base;
  background: var(--el-fill-color-light);
}
.share__track-main {
  flex: 1;
  min-width: 0;
}
.share__track-label {
  margin: 0;
  font-size: lp.$font-size-extra-small;
  color: var(--el-text-color-secondary);
}
.share__track-value {
  margin: 2px 0 0;
  font-size: lp.$font-size-large;
  font-weight: lp.$font-weight-semibold;
  color: var(--el-text-color-primary);
  word-break: break-all;
  &.is-empty {
    font-size: lp.$font-size-medium;
    font-weight: lp.$font-weight-regular;
    color: var(--el-text-color-placeholder);
  }
}
.share__copy {
  flex: none;
  height: lp.$component-size-mobile;
}
</style>
