<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="flex-x-between">
          <span>路由配置</span>
          <el-button type="primary" @click="handleOpenDialog('0')">
            <el-icon class="mr-1"><Plus /></el-icon>新增目录
          </el-button>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="menuTableData"
        row-key="id"
        border
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <el-table-column label="菜单名称" min-width="200">
          <template #default="scope">
            <el-icon v-if="scope.row.icon && scope.row.icon.startsWith('el-icon')" style="vertical-align: -0.15em">
              <component :is="scope.row.icon.replace('el-icon-', '')" />
            </el-icon>
            <div v-else-if="scope.row.icon" class="menu-svg-icon" :class="`i-svg:${scope.row.icon}`" />
            <span class="ml-1">{{ scope.row.menuName }}</span>
          </template>
        </el-table-column>

        <el-table-column label="类型" align="center" width="80">
          <template #default="scope">
            <el-tag v-if="scope.row.menuType === 0" type="warning">目录</el-tag>
            <el-tag v-else-if="scope.row.menuType === 1" type="success">菜单</el-tag>
            <el-tag v-else type="danger">按钮</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="路由路径" width="140" prop="menuUrl" />
        <el-table-column label="路由名称" width="140" prop="routeName">
          <template #default="{ row }">{{ row.routeName || "-" }}</template>
        </el-table-column>
        <el-table-column label="组件路径" min-width="180" prop="component" show-overflow-tooltip>
          <template #default="{ row }">{{ row.component || "-" }}</template>
        </el-table-column>
        <el-table-column label="权限标识" width="160" prop="permission" show-overflow-tooltip>
          <template #default="{ row }">{{ row.permission || "-" }}</template>
        </el-table-column>
        <el-table-column label="排序" width="70" align="center" prop="sortOrder" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.visible === 1" type="success">显示</el-tag>
            <el-tag v-else type="info">隐藏</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <el-button
              v-if="scope.row.menuType !== 2"
              type="primary"
              link
              size="small"
              @click="handleOpenDialog(scope.row.id)"
            >
              新增
            </el-button>
            <el-button type="primary" link size="small" @click="handleOpenDialog(undefined, scope.row)">
              编辑
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.title"
      width="560px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
        <el-form-item label="父级菜单" prop="parentId">
          <el-tree-select
            v-model="formData.parentId"
            placeholder="选择上级菜单，顶级请选「顶级目录」"
            :data="parentOptions"
            filterable
            check-strictly
            :render-after-expand="false"
          />
        </el-form-item>

        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="formData.menuName" placeholder="请输入菜单名称" />
        </el-form-item>

        <el-form-item label="菜单类型" prop="menuType">
          <el-radio-group v-model="formData.menuType">
            <el-radio :value="0">目录</el-radio>
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item
          v-if="formData.menuType !== 2"
          label="路由路径"
          prop="menuUrl"
        >
          <el-input
            v-model="formData.menuUrl"
            :placeholder="formData.menuType === 0 ? '目录路径，如 /tool' : '菜单路径，如 user（相对父级）'"
          />
        </el-form-item>

        <el-form-item v-if="formData.menuType === 1" label="路由名称" prop="routeName">
          <el-input v-model="formData.routeName" placeholder="如 User，需与页面 defineOptions.name 一致" />
        </el-form-item>

        <el-form-item v-if="formData.menuType === 1" label="组件路径" prop="component">
          <el-input v-model="formData.component" placeholder="相对 src/views/ 的路径，如 system/user/index">
            <template #prepend>src/views/</template>
            <template #append>.vue</template>
          </el-input>
        </el-form-item>

        <el-form-item v-if="formData.menuType === 0" label="跳转地址" prop="redirect">
          <el-input v-model="formData.redirect" placeholder="如 /tool/trace" />
        </el-form-item>

        <el-form-item v-if="formData.menuType === 2" label="权限标识" prop="permission">
          <el-input v-model="formData.permission" placeholder="如 sys:user:add" />
        </el-form-item>

        <el-form-item v-if="formData.menuType !== 2" label="图标" prop="icon">
          <IconSelect v-model="formData.icon" />
        </el-form-item>

        <el-form-item label="显示状态" prop="visible">
          <el-radio-group v-model="formData.visible">
            <el-radio :value="1">显示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="formData.menuType === 1" label="缓存页面">
          <el-radio-group v-model="formData.keepAlive">
            <el-radio :value="1">开启</el-radio>
            <el-radio :value="0">关闭</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="排序" prop="sortOrder">
          <el-input-number
            v-model="formData.sortOrder"
            style="width: 120px"
            controls-position="right"
            :min="0"
          />
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
import MenuAPI, { type SysMenuVO, type MenuForm } from "@/api/system/menu";

defineOptions({
  name: "Menu",
  inheritAttrs: false,
});

const loading = ref(false);
const submitLoading = ref(false);
const menuTableData = ref<SysMenuVO[]>([]);
const parentOptions = ref<OptionType[]>([]);

const dialog = reactive({
  visible: false,
  title: "",
});

const formRef = ref();
const formData = reactive<MenuForm>({
  parentId: "0",
  menuName: "",
  menuUrl: "",
  menuType: 1,
  icon: "",
  visible: 1,
  keepAlive: 0,
  alwaysShow: 0,
  sortOrder: 1,
  component: "",
  routeName: "",
  redirect: "",
  permission: "",
});

const rules = {
  parentId: [{ required: true, message: "请选择父级菜单", trigger: "blur" }],
  menuName: [{ required: true, message: "请输入菜单名称", trigger: "blur" }],
  menuUrl: [
    {
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        if (formData.menuType !== 2 && !value) {
          callback(new Error("请输入路由路径"));
          return;
        }
        callback();
      },
      trigger: "blur",
    },
  ],
  component: [
    {
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        if (formData.menuType === 1 && !value) {
          callback(new Error("请输入组件路径"));
          return;
        }
        callback();
      },
      trigger: "blur",
    },
  ],
  permission: [
    {
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        if (formData.menuType === 2 && !value) {
          callback(new Error("请输入权限标识"));
          return;
        }
        callback();
      },
      trigger: "blur",
    },
  ],
};

/** 将扁平列表组装为树 */
function buildTree(list: SysMenuVO[]): SysMenuVO[] {
  const map = new Map<string, SysMenuVO>();
  list.forEach((item) => map.set(item.id!, { ...item, children: [] }));
  const roots: SysMenuVO[] = [];
  list.forEach((item) => {
    const node = map.get(item.id!);
    const parent = item.parentId && map.get(item.parentId);
    if (parent) {
      parent.children!.push(node!);
    } else {
      roots.push(node!);
    }
  });
  return roots;
}

/** 组装父级下拉树（目录与菜单可作为父级） */
function buildParentOptions(list: SysMenuVO[]): OptionType[] {
  const tree = buildTree(list.filter((item) => item.menuType !== 2));
  const toOption = (item: SysMenuVO): OptionType => ({
    value: item.id ?? "",
    label: item.menuName ?? "",
    children: item.children?.map(toOption),
  });
  return [{ value: "0", label: "顶级目录", children: tree.map(toOption) }];
}

function loadMenus() {
  loading.value = true;
  MenuAPI.getList()
    .then((list) => {
      menuTableData.value = buildTree(list || []);
      parentOptions.value = buildParentOptions(list || []);
    })
    .catch((error) => {
      console.error("加载菜单失败", error);
    })
    .finally(() => {
      loading.value = false;
    });
}

function handleOpenDialog(parentId?: string, row?: SysMenuVO) {
  if (row) {
    dialog.title = "编辑路由";
    Object.assign(formData, {
      id: row.id,
      parentId: row.parentId || "0",
      menuName: row.menuName,
      menuUrl: row.menuUrl,
      menuType: row.menuType ?? 1,
      icon: row.icon,
      visible: row.visible ?? 1,
      keepAlive: row.keepAlive ?? 0,
      alwaysShow: row.alwaysShow ?? 0,
      sortOrder: row.sortOrder ?? 1,
      component: row.component,
      routeName: row.routeName,
      redirect: row.redirect,
      permission: row.permission,
    });
  } else {
    // 新增：顶级节点默认为目录，其余默认菜单
    const isTop = parentId === "0";
    formData.menuType = isTop ? 0 : 1;
    dialog.title = isTop ? "新增目录" : "新增路由";
    formData.parentId = parentId || "0";
  }
  dialog.visible = true;
}

function resetForm() {
  formRef.value?.resetFields?.();
  formData.id = undefined;
  formData.parentId = "0";
  formData.menuName = "";
  formData.menuUrl = "";
  formData.menuType = 1;
  formData.icon = "";
  formData.visible = 1;
  formData.keepAlive = 0;
  formData.alwaysShow = 0;
  formData.sortOrder = 1;
  formData.component = "";
  formData.routeName = "";
  formData.redirect = "";
  formData.permission = "";
}

function handleSubmit() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    if (formData.id && formData.parentId === formData.id) {
      ElMessage.error("父级菜单不能为当前菜单");
      return;
    }
    // 目录类型固定使用 Layout 组件
    const payload = {
      ...formData,
      component: formData.menuType === 0 ? "Layout" : formData.component,
      alwaysShow: formData.alwaysShow ?? 0,
    };
    submitLoading.value = true;
    const api = formData.id ? MenuAPI.update(payload) : MenuAPI.add(payload);
    api
      .then(() => {
        ElMessage.success(formData.id ? "修改成功" : "新增成功");
        dialog.visible = false;
        loadMenus();
      })
      .catch((error) => {
        console.error(error);
      })
      .finally(() => {
        submitLoading.value = false;
      });
  });
}

function handleDelete(row: SysMenuVO) {
  if (!row.id) return;
  const hasChildren = (row.children && row.children.length > 0) || false;
  ElMessageBox.confirm(
    hasChildren
      ? `菜单「${row.menuName}」下存在子菜单，删除后将一并删除全部子菜单，确认删除吗？`
      : `确认删除菜单「${row.menuName}」吗？`,
    "警告",
    {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    }
  )
    .then(() => {
      // 递归收集自身与所有子节点 ID
      const ids: string[] = [];
      const collect = (node: SysMenuVO) => {
        ids.push(node.id!);
        node.children?.forEach(collect);
      };
      collect(row);
      MenuAPI.del(ids).then(() => {
        ElMessage.success("删除成功");
        loadMenus();
      });
    })
    .catch(() => {});
}

onMounted(() => {
  loadMenus();
});
</script>

<style lang="scss" scoped>
.app-container {
  padding: 16px;
}

.menu-svg-icon {
  display: inline-block;
  width: 14px;
  height: 14px;
  color: currentcolor;
  vertical-align: -0.15em;
}
</style>
