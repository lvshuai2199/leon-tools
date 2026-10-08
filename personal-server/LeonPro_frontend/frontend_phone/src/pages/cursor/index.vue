<script setup lang="ts">
/**
 * Cursor 任务：每人保存自己的 Key。电脑左侧列表切换；手机点当前任务弹出完整列表，直接跳到某一条。
 */
import { computed, nextTick, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight, Key, Plus } from '@element-plus/icons-vue'
import PageBar from '@/components/PageBar.vue'
import IconAction from '@/components/IconAction.vue'
import StateBlock from '@/components/StateBlock.vue'
import ResponsiveDialog from '@/components/ResponsiveDialog.vue'
import SheetSelect from '@/components/SheetSelect.vue'
import { cursorStatusText, cursorTone } from '@/api/cursor'
import { useCursorTasks } from './useCursorTasks'

const page = useCursorTasks()
const apiKey = ref('')
const keyOpen = ref(false)
const createOpen = ref(false)
const prompt = ref('')
const repoUrl = ref('')
const startingRef = ref('')
const autoCreatePr = ref(true)
const followText = ref('')
const repoManual = ref(false)
const listOpen = ref(false)
const taskQuery = ref('')
const taskListEl = ref<HTMLElement | null>(null)

function repoLabel(url?: string) {
  if (!url) return ''
  return url.replace(/^https?:\/\/(www\.)?github\.com\//i, '').replace(/\.git$/, '')
}

const currentTask = computed(() => page.tasks.value.find((t) => t.id === page.selectedId.value))
const filteredTasks = computed(() => {
  const q = taskQuery.value.trim().toLowerCase()
  if (!q) return page.tasks.value
  return page.tasks.value.filter((t) => {
    const blob = `${t.name || ''} ${repoLabel(t.repoUrl)} ${cursorStatusText(t.agentStatus, t.runStatus)}`.toLowerCase()
    return blob.includes(q)
  })
})

function openTaskList() {
  taskQuery.value = ''
  listOpen.value = true
}

function pickTask(id: string) {
  listOpen.value = false
  page.select(id)
}

watch(listOpen, async (open) => {
  if (!open) return
  await nextTick()
  taskListEl.value?.querySelector('[data-current="1"]')?.scrollIntoView({ block: 'center' })
})

const repoOptions = computed(() =>
  page.repos.value.map((r) => ({
    label: r.owner ? `${r.owner}/${r.name}` : r.name,
    value: r.url,
    desc: r.url,
  })),
)

watch(createOpen, (open) => {
  if (!open) return
  repoManual.value = false
  page.loadRepos()
})

watch(repoOptions, (opts) => {
  if (repoUrl.value && opts.length && !opts.some((o) => o.value === repoUrl.value)) repoManual.value = true
})

async function onSaveKey() {
  await page.saveKey(apiKey.value)
  apiKey.value = ''
  keyOpen.value = false
}

async function onCreate() {
  await page.createTask({
    prompt: prompt.value,
    repoUrl: repoUrl.value,
    startingRef: startingRef.value,
    autoCreatePr: autoCreatePr.value,
  })
  prompt.value = ''
  createOpen.value = false
}

async function onFollow() {
  const text = followText.value
  await page.follow(text)
  followText.value = ''
}

async function onClearKey() {
  await page.clearKey()
  keyOpen.value = false
}
</script>

<template>
  <div class="cursor">
    <PageBar title="Cursor 任务" back="/" desc="用你自己的 Key 发任务，点任务名切换预览">
      <template #actions="{ mobile }">
        <template v-if="page.board.value.configured">
          <IconAction v-if="mobile" :icon="Key" label="我的 Key" @click="keyOpen = true" />
          <el-button v-else @click="keyOpen = true">我的 Key</el-button>
          <IconAction v-if="mobile" :icon="Plus" label="新建任务" @click="createOpen = true" />
          <el-button v-else type="primary" @click="createOpen = true">新建任务</el-button>
        </template>
      </template>
    </PageBar>

    <StateBlock v-if="page.loading.value && !page.board.value.tasks.length && !page.board.value.configured" type="loading" title="正在加载" />
    <StateBlock v-else-if="page.error.value" type="error" :title="page.error.value" @retry="page.reload(false)" />

    <section v-else-if="!page.board.value.configured" class="keycard">
      <h2>先保存你的 Cursor Key</h2>
      <ol>
        <li>
          打开
          <a href="https://cursor.com/dashboard/integrations" target="_blank" rel="noopener">Cursor Dashboard → Integrations</a>
        </li>
        <li>登录你自己的 Cursor 账号。每个用户用自己的 Key，互相看不到任务。</li>
        <li>创建 User API Key，复制整串（一般以 cursor_ 开头）。</li>
        <li>粘贴到下面。保存后会同步这个账号在 Cursor 上已有的云端任务，页面之后只显示 Key 末四位。</li>
      </ol>
      <el-input v-model="apiKey" type="password" show-password placeholder="cursor_..." autocomplete="off" />
      <el-button type="primary" :loading="page.savingKey.value" :disabled="!apiKey.trim()" @click="onSaveKey">保存</el-button>
    </section>

    <template v-else>
      <p v-if="page.board.value.warning" class="warn">{{ page.board.value.warning }}</p>
      <StateBlock
        v-if="!page.tasks.value.length"
        type="empty"
        icon="document"
        title="这个账号还没有云端任务"
        desc="保存 Key 后会列出 Cursor 里已有的任务，也可以在这里新建"
      >
        <el-button type="primary" @click="createOpen = true">新建任务</el-button>
      </StateBlock>

      <div v-else class="work">
        <aside class="switcher">
          <div class="switcher__list" role="tablist" aria-label="任务">
            <button
              v-for="t in page.tasks.value"
              :key="t.id"
              type="button"
              role="tab"
              class="chip"
              :class="{ 'is-on': t.id === page.selectedId.value }"
              :aria-selected="t.id === page.selectedId.value"
              @click="page.select(t.id)"
            >
              <span class="chip__top">
                <i class="dot" :class="`dot--${cursorTone(t.agentStatus, t.runStatus)}`" />
                <span class="chip__name">{{ t.name || '未命名任务' }}</span>
              </span>
              <span class="chip__meta">
                {{ cursorStatusText(t.agentStatus, t.runStatus) }}
                <template v-if="repoLabel(t.repoUrl)"> · {{ repoLabel(t.repoUrl) }}</template>
              </span>
            </button>
          </div>

          <div class="switcher__bar">
            <button type="button" class="step" aria-label="上一条" :disabled="page.tasks.value.length < 2" @click="page.step(-1)">
              <el-icon :size="18"><ArrowLeft /></el-icon>
            </button>
            <button type="button" class="now" @click="openTaskList">
              <span class="now__name">{{ currentTask?.name || '未命名任务' }}</span>
              <span class="now__meta">
                {{ cursorStatusText(currentTask?.agentStatus, currentTask?.runStatus) }}
                <template v-if="repoLabel(currentTask?.repoUrl)"> · {{ repoLabel(currentTask?.repoUrl) }}</template>
                · {{ page.index.value + 1 }}/{{ page.tasks.value.length }} · 全部
              </span>
            </button>
            <button type="button" class="step" aria-label="下一条" :disabled="page.tasks.value.length < 2" @click="page.step(1)">
              <el-icon :size="18"><ArrowRight /></el-icon>
            </button>
          </div>
        </aside>

        <el-drawer v-model="listOpen" direction="btt" size="78%" title="全部任务" append-to-body class="cursor-task-sheet">
          <div class="sheet">
            <el-input v-model="taskQuery" clearable placeholder="搜任务名或仓库" />
            <ul ref="taskListEl" class="sheet__list" role="listbox">
              <li v-for="t in filteredTasks" :key="t.id">
                <button
                  type="button"
                  class="row"
                  :class="{ 'is-on': t.id === page.selectedId.value }"
                  :data-current="t.id === page.selectedId.value ? '1' : undefined"
                  @click="pickTask(t.id)"
                >
                  <span class="row__name">{{ t.name || '未命名任务' }}</span>
                  <span class="row__meta">
                    <i class="dot" :class="`dot--${cursorTone(t.agentStatus, t.runStatus)}`" />
                    {{ cursorStatusText(t.agentStatus, t.runStatus) }}
                    <template v-if="repoLabel(t.repoUrl)"> · {{ repoLabel(t.repoUrl) }}</template>
                    <template v-if="t.updatedAt"> · {{ t.updatedAt }}</template>
                  </span>
                </button>
              </li>
            </ul>
            <p v-if="!filteredTasks.length" class="sheet__empty">没有匹配的任务</p>
          </div>
        </el-drawer>

        <section v-if="page.detail.value" class="preview" role="tabpanel">
          <header class="preview__head">
            <div>
              <h2>{{ page.detail.value.name || '未命名任务' }}</h2>
              <p class="preview__meta">
                <el-tag size="small" :type="cursorTone(page.detail.value.agentStatus, page.detail.value.runStatus)">
                  {{ cursorStatusText(page.detail.value.agentStatus, page.detail.value.runStatus) }}
                </el-tag>
                <span v-if="page.detail.value.updatedAt">{{ page.detail.value.updatedAt }}</span>
              </p>
            </div>
          </header>
          <p v-if="page.detail.value.repoUrl" class="preview__repo">{{ page.detail.value.repoUrl }}</p>
          <p v-if="page.detail.value.promptPreview" class="preview__prompt">{{ page.detail.value.promptPreview }}</p>
          <div class="preview__links">
            <a v-if="page.detail.value.agentUrl" :href="page.detail.value.agentUrl" target="_blank" rel="noopener">在 Cursor 打开</a>
            <a v-if="page.detail.value.prUrl" :href="page.detail.value.prUrl" target="_blank" rel="noopener">查看 PR</a>
            <span v-if="page.detail.value.branchName">{{ page.detail.value.branchName }}</span>
          </div>
          <pre class="preview__result">{{ page.detail.value.resultText || (page.busy.value ? '正在执行，结果会自动刷新' : '还没有结果') }}</pre>
          <div class="follow">
            <el-input
              v-model="followText"
              type="textarea"
              :rows="3"
              maxlength="8000"
              placeholder="接着让它改什么"
              :disabled="page.busy.value || page.sending.value"
            />
            <div class="follow__bar">
              <el-button v-if="page.busy.value" :loading="page.sending.value" @click="page.cancel()">停止</el-button>
              <el-button v-else type="primary" :loading="page.sending.value" :disabled="!followText.trim()" @click="onFollow">发送</el-button>
            </div>
          </div>
        </section>
        <StateBlock v-else-if="page.detailError.value" type="error" :title="page.detailError.value" @retry="page.select(page.selectedId.value)" />
        <StateBlock v-else type="loading" title="正在打开这条任务" />
      </div>
    </template>

    <ResponsiveDialog v-model="keyOpen" title="我的 Key">
      <p class="hint">当前末四位 {{ page.board.value.hint }}。换一把会覆盖，已有任务还在。</p>
      <el-input v-model="apiKey" type="password" show-password placeholder="新的 cursor_ Key" autocomplete="off" />
      <template #footer>
        <el-button @click="onClearKey">清除</el-button>
        <el-button type="primary" :loading="page.savingKey.value" :disabled="!apiKey.trim()" @click="onSaveKey">保存</el-button>
      </template>
    </ResponsiveDialog>

    <ResponsiveDialog v-model="createOpen" title="新建任务">
      <div class="form">
        <el-input v-model="prompt" type="textarea" :rows="4" maxlength="8000" placeholder="要做什么" />
        <p v-if="page.reposLoading.value" class="form__note">正在读取这个 Key 能访问的仓库，可能要几十秒</p>
        <p v-else-if="page.reposWarning.value" class="form__note form__note--warn">{{ page.reposWarning.value }}</p>
        <SheetSelect
          v-if="!repoManual && repoOptions.length"
          v-model="repoUrl"
          :options="repoOptions"
          title="选择仓库"
          placeholder="选择仓库"
        />
        <el-input v-else v-model="repoUrl" placeholder="https://github.com/你的账号/仓库" />
        <button v-if="repoOptions.length" type="button" class="form__link" @click="repoManual = !repoManual">
          {{ repoManual ? '从列表选' : '手填地址' }}
        </button>
        <el-input v-model="startingRef" placeholder="起始分支，留空用默认分支" />
        <label class="form__pr"><el-switch v-model="autoCreatePr" />完成后开 PR</label>
      </div>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="page.sending.value" :disabled="!prompt.trim() || !repoUrl.trim()" @click="onCreate">发送</el-button>
      </template>
    </ResponsiveDialog>
  </div>
</template>

<style scoped lang="scss">
.cursor {
  padding-bottom: lp.$space-6;
  @include lp.mobile {
    padding: lp.$page-padding-mobile;
  }
}
.keycard,
.preview {
  padding: lp.$card-padding;
  border-radius: lp.$radius-card;
  background: var(--el-bg-color);
  box-shadow: var(--lp-shadow-card);
}
.keycard {
  display: flex;
  flex-direction: column;
  gap: lp.$space-3;
  max-width: 640px;
  h2 {
    margin: 0;
    font-size: lp.$font-size-medium;
  }
  ol {
    margin: 0;
    padding-left: 1.2em;
    color: var(--el-text-color-regular);
    line-height: 1.6;
  }
  a {
    color: var(--el-color-primary);
  }
}
.warn {
  margin: 0 0 lp.$space-3;
  color: var(--el-color-danger);
}
.work {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: lp.$space-4;
  align-items: start;
}
.switcher__list {
  display: flex;
  flex-direction: column;
  gap: lp.$space-2;
  max-height: calc(100vh - 180px);
  overflow: auto;
}
.switcher__bar {
  display: none;
}
.chip {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 2px;
  width: 100%;
  min-height: 56px;
  padding: lp.$space-2 lp.$space-3;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: lp.$radius-base;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  &.is-on {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }
}
.chip__top {
  display: flex;
  align-items: center;
  gap: lp.$space-2;
  min-width: 0;
}
.chip__name,
.row__name,
.now__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
.chip__name {
  white-space: nowrap;
}
.chip__meta,
.row__meta,
.now__meta {
  color: var(--el-text-color-secondary);
  font-size: lp.$font-size-base;
  line-height: 1.4;
}
.step {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: lp.$radius-base;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  &:disabled { opacity: 0.4; }
}
.now {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  min-width: 0;
  min-height: 44px;
  padding: 0 lp.$space-2;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.now__name {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  white-space: normal;
  color: var(--el-text-color-primary);
  font-size: lp.$font-size-medium;
  line-height: 1.3;
}
.sheet {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: lp.$space-3;
  min-height: 0;
}
.sheet__list {
  margin: 0;
  padding: 0;
  list-style: none;
  overflow: auto;
  flex: 1;
}
.sheet__empty {
  margin: 0;
  color: var(--el-text-color-secondary);
  text-align: center;
}
.row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  min-height: 64px;
  padding: lp.$space-3 0;
  border: none;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: transparent;
  text-align: left;
  cursor: pointer;
  &.is-on .row__name { color: var(--el-color-primary); }
}
.row__name {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  white-space: normal;
  color: var(--el-text-color-primary);
  font-size: lp.$font-size-medium;
  line-height: 1.35;
}
.row__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-primary);
}
.dot--success { background: var(--el-color-success); }
.dot--danger { background: var(--el-color-danger); }
.dot--info { background: var(--el-text-color-placeholder); }
.preview__head h2 {
  margin: 0;
  font-size: lp.$font-size-medium;
}
.preview__meta,
.preview__repo,
.preview__prompt,
.preview__links {
  margin: lp.$space-2 0 0;
  color: var(--el-text-color-secondary);
  font-size: lp.$font-size-base;
}
.preview__meta {
  display: flex;
  align-items: center;
  gap: lp.$space-2;
}
.preview__links {
  display: flex;
  flex-wrap: wrap;
  gap: lp.$space-3;
  a { color: var(--el-color-primary); }
}
.preview__result {
  margin: lp.$space-3 0 0;
  max-height: 46vh;
  overflow: auto;
  padding: lp.$space-3;
  border-radius: lp.$radius-base;
  background: var(--el-fill-color-light);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: lp.$font-size-base;
  line-height: 1.6;
}
.follow {
  margin-top: lp.$space-3;
}
.follow__bar {
  display: flex;
  justify-content: flex-end;
  margin-top: lp.$space-2;
}
.hint {
  margin: 0 0 lp.$space-3;
  color: var(--el-text-color-secondary);
}
.form {
  display: flex;
  flex-direction: column;
  gap: lp.$space-3;
}
.form__pr {
  display: flex;
  align-items: center;
  gap: lp.$space-2;
}
.form__note {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: lp.$font-size-base;
}
.form__note--warn {
  color: var(--el-color-danger);
}
.form__link {
  align-self: flex-start;
  padding: 0;
  border: none;
  background: none;
  color: var(--el-color-primary);
  font-size: lp.$font-size-base;
  cursor: pointer;
}

@include lp.mobile {
  .work {
    display: block;
  }
  .switcher {
    position: sticky;
    top: var(--topbar-h, 44px);
    z-index: 4;
    margin: 0 calc(-1 * #{lp.$page-padding-mobile}) lp.$space-3;
    padding: lp.$space-2 lp.$page-padding-mobile;
    background: var(--el-bg-color);
  }
  .switcher__list { display: none; }
  .switcher__bar {
    display: grid;
    grid-template-columns: 44px minmax(0, 1fr) 44px;
    gap: lp.$space-2;
    align-items: center;
  }
  .preview {
    margin-top: lp.$space-3;
  }
}
</style>

<style lang="scss">
.cursor-task-sheet .el-drawer__body {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
