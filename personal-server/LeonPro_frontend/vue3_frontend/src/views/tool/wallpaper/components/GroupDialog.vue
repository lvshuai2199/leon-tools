<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑组' : '新建组'"
    width="480px"
    append-to-body
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @open="onOpen"
    @closed="onClosed"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" maxlength="50" placeholder="例如：风景" />
      </el-form-item>
      <el-form-item label="key" prop="groupKey">
        <el-input
          v-model="form.groupKey"
          maxlength="50"
          placeholder="小写字母、数字和 -，例如 landscape"
        />
        <div v-if="keyChanged" class="warn-tip">改了 key 以后，旧的接口地址会失效</div>
        <div v-else class="hint-tip">修改后接口地址会变化</div>
      </el-form-item>
      <el-form-item label="说明" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="2" maxlength="200" />
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number
          v-model="form.sort"
          :min="0"
          :max="9999"
          controls-position="right"
          :placeholder="isEdit ? '' : '留空排到最后'"
        />
      </el-form-item>
      <el-form-item label="对外开放" prop="isPublic">
        <el-switch v-model="form.isPublic" />
      </el-form-item>
      <el-form-item v-if="isEdit" label="访问密钥">
        <div class="token-row">
          <el-input :model-value="token || '（未生成）'" readonly class="token-input" />
          <el-button :disabled="!token" @click="copyText(token!)">复制</el-button>
          <el-button text :loading="regenerating" @click="onRegenerate">重新生成</el-button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from "element-plus";
import WallpaperAPI, { type WallpaperGroupForm, type WallpaperGroupVO } from "@/api/tool/wallpaper";

const props = defineProps<{ modelValue: boolean; group?: WallpaperGroupVO | null }>();
const emit = defineEmits(["update:modelValue", "saved", "token-changed"]);

const formRef = ref<FormInstance>();
const saving = ref(false);
const regenerating = ref(false);
const token = ref<string | undefined>();
/** 保存成功后先关弹窗，等关闭动画结束（@closed）再提示「已保存」 */
let savedToastPending = false;
const form = reactive<WallpaperGroupForm>({
  name: "",
  groupKey: "",
  description: "",
  sort: undefined,
  isPublic: true,
});

/** 弹框点「取消」/ 关闭时返回 false，不把 "cancel" 抛出去（避免控制台 Uncaught (in promise)） */
const confirmed = (p: Promise<unknown>) =>
  p.then(
    () => true,
    () => false
  );

const isEdit = computed(() => !!props.group?.id);
const keyChanged = computed(() => isEdit.value && form.groupKey !== props.group?.groupKey);

const rules: FormRules = {
  name: [{ required: true, message: "请填写组名", trigger: "blur" }],
  groupKey: [
    { required: true, message: "请填写 key", trigger: "blur" },
    { pattern: /^[a-z0-9-]+$/, message: "只允许小写字母、数字和 -", trigger: "blur" },
  ],
};

function onOpen() {
  const g = props.group;
  form.name = g?.name ?? "";
  form.groupKey = g?.groupKey ?? "";
  form.description = g?.description ?? "";
  // 新建时留空，由后端排到最后（后端取当前最大 sort + 1）
  form.sort = g?.sort ?? undefined;
  form.isPublic = g ? g.isPublic === true || Number(g.isPublic) === 1 : true;
  token.value = g?.token;
  savedToastPending = false;
  nextTick(() => formRef.value?.clearValidate());
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (keyChanged.value) {
    const ok = await confirmed(
      ElMessageBox.confirm(
        "改了 key 以后，旧的接口地址会失效，用量小工具里的地址也要重新粘贴。确定修改？",
        "提示",
        { type: "warning" }
      )
    );
    if (!ok) return;
  }
  saving.value = true;
  try {
    const res = isEdit.value
      ? await WallpaperAPI.updateGroup(props.group!.id, { ...form })
      : await WallpaperAPI.createGroup({ ...form });
    savedToastPending = true;
    emit("update:modelValue", false);
    emit("saved", res as WallpaperGroupVO | undefined);
  } catch {
    // request 拦截器已提示错误（如 key 重复），保持弹窗让用户修改
  } finally {
    saving.value = false;
  }
}

function onClosed() {
  if (!savedToastPending) return;
  savedToastPending = false;
  ElMessage.success("已保存");
}

async function onRegenerate() {
  const ok = await confirmed(
    ElMessageBox.confirm(
      "旧地址会立即失效，用量小工具里的地址也要重新粘贴。确定重新生成？",
      "重新生成密钥",
      { type: "warning", confirmButtonText: "重新生成" }
    )
  );
  if (!ok) return;
  regenerating.value = true;
  try {
    const t = await WallpaperAPI.regenerateToken(props.group!.id);
    token.value = t;
    emit("token-changed", t);
    ElMessage.success("已生成新密钥");
  } catch {
    // request 拦截器已提示
  } finally {
    regenerating.value = false;
  }
}

function copyText(text: string) {
  navigator.clipboard.writeText(text).then(
    () => ElMessage.success("已复制"),
    () => ElMessage.error("复制失败，请手动复制")
  );
}
</script>

<style scoped lang="scss">
.warn-tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: #d14343;
}
.hint-tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
.token-row {
  display: flex;
  gap: 8px;
  width: 100%;
  .token-input {
    flex: 1;
    min-width: 0;
    :deep(.el-input__inner) {
      font-family: ui-monospace, Menlo, Consolas, monospace;
    }
  }
  .el-button + .el-button {
    margin-left: 0;
  }
}
</style>
