<template>
  <div class="app-container">
    <div class="search-bar">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true">
        <el-form-item prop="roleName" label="角色名称">
          <el-input
            v-model="queryParams.roleName"
            placeholder="角色名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" icon="search" @click="handleQuery">搜索</el-button>
          <el-button icon="refresh" @click="handleResetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card shadow="never">
      <div class="mb-10px">
        <el-button type="success" icon="plus" @click="handleOpenDialog()">新增</el-button>
        <el-button type="danger" :disabled="ids.length === 0" icon="delete" @click="handleDelete()">
          删除
        </el-button>
      </div>

      <el-table
        ref="dataTableRef"
        v-loading="loading"
        :data="roleList"
        highlight-current-row
        border
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" :selectable="(row: RolePageVO) => !isRootRole(row)" />
        <el-table-column label="角色名称" prop="roleName" min-width="120">
          <template #default="{ row }">
            <span>{{ row.roleName }}</span>
            <el-tag v-if="isRootRole(row)" type="danger" size="small" class="ml-1">系统默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色描述" prop="description" min-width="180" show-overflow-tooltip />

        <el-table-column label="状态" align="center" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.isDisabled === 0" type="success">正常</el-tag>
            <el-tag v-else type="info">禁用</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" prop="createTime" width="180" align="center" />

        <el-table-column fixed="right" label="操作" width="180">
          <template #default="scope">
            <template v-if="isRootRole(scope.row)">
              <el-text type="info" size="small">拥有全部权限，不可配置</el-text>
            </template>
            <template v-else>
              <el-button
                type="primary"
                size="small"
                link
                icon="edit"
                @click="handleOpenDialog(scope.row)"
              >
                编辑
              </el-button>
              <el-button
                type="warning"
                size="small"
                link
                icon="Lock"
                @click="handleOpenPermission(scope.row)"
              >
                权限
              </el-button>
              <el-button
                type="danger"
                size="small"
                link
                icon="delete"
                @click="handleDelete(scope.row.id)"
              >
                删除
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-if="total > 0"
        v-model:total="total"
        v-model:page="queryParams.current"
        v-model:limit="queryParams.size"
        @pagination="handleQuery"
      />
    </el-card>

    <!-- 角色表单弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.title"
      width="500px"
      @close="handleCloseDialog"
    >
      <el-form ref="roleFormRef" :model="formData" :rules="rules" label-width="100px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="formData.roleName" placeholder="请输入角色名称" />
        </el-form-item>

        <el-form-item label="角色描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入角色描述"
          />
        </el-form-item>

        <el-form-item label="状态" prop="isDisabled">
          <el-radio-group v-model="formData.isDisabled">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="loading" @click="handleSubmit">确 定</el-button>
          <el-button @click="handleCloseDialog">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 角色权限（可访问路由）设置弹窗 -->
    <el-dialog
      v-model="permissionDialog.visible"
      title="分配可访问路由"
      width="480px"
    >
      <el-tree
        ref="menuTreeRef"
        :data="menuTreeData"
        :props="{ label: 'label', children: 'children' }"
        show-checkbox
        node-key="id"
        :default-checked-keys="permissionDialog.checkedKeys"
        default-expand-all
        highlight-current
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="permissionDialog.visible = false">取 消</el-button>
          <el-button
            type="primary"
            :loading="permissionDialog.loading"
            @click="handlePermissionSubmit"
          >
            确 定
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: "Role",
  inheritAttrs: false,
});

import RoleAPI, { RolePageVO, RoleForm, RolePageQuery } from "@/api/system/role";
import MenuAPI, { SysMenuVO } from "@/api/system/menu";
import { isRootRole } from "@/utils/role";

const queryFormRef = ref();
const roleFormRef = ref();

const loading = ref(false);
const ids = ref<string[]>([]);
const total = ref(0);

// 角色权限（可访问路由）设置
const menuTreeRef = ref<any>();
const menuTreeData = ref<any[]>([]);
const permissionDialog = reactive({
  visible: false,
  loading: false,
  roleId: "",
  checkedKeys: [] as string[],
});

interface RoleQuery extends RolePageQuery {
  current: number;
  size: number;
}
const queryParams = reactive<RoleQuery>({
  current: 1,
  size: 10,
});

// 角色表格数据
const roleList = ref<RolePageVO[]>([]);

// 弹窗
const dialog = reactive({
  title: "",
  visible: false,
});

// 角色表单
const initialFormData: RoleForm = {
  roleName: "",
  description: "",
  isDisabled: 0,
};
const formData = reactive<RoleForm>({ ...initialFormData });

const rules = reactive({
  roleName: [{ required: true, message: "请输入角色名称", trigger: "blur" }],
});

// 查询
function handleQuery() {
  loading.value = true;
  RoleAPI.getPage(queryParams)
    .then((data) => {
      roleList.value = data.records ?? [];
      total.value = Number(data.total ?? 0);
    })
    .finally(() => {
      loading.value = false;
    });
}

// 重置查询
function handleResetQuery() {
  queryFormRef.value.resetFields();
  queryParams.current = 1;
  handleQuery();
}

// 行复选框选中
function handleSelectionChange(selection: any) {
  ids.value = selection.map((item: any) => item.id);
}

// 打开角色弹窗
function handleOpenDialog(row?: RolePageVO) {
  if (row && isRootRole(row)) {
    ElMessage.warning("系统默认角色 ROOT 不允许修改");
    return;
  }
  dialog.visible = true;
  if (row) {
    dialog.title = "修改角色";
    Object.assign(formData, row);
  } else {
    dialog.title = "新增角色";
    Object.assign(formData, initialFormData);
  }
}

// 提交角色表单
function handleSubmit() {
  roleFormRef.value.validate((valid: any) => {
    if (!valid) return;
    loading.value = true;
    const api = formData.id ? RoleAPI.update(formData) : RoleAPI.add(formData);
    api
      .then(() => {
        ElMessage.success(formData.id ? "修改成功" : "新增成功");
        handleCloseDialog();
        handleQuery();
      })
      .finally(() => (loading.value = false));
  });
}

// 关闭弹窗
function handleCloseDialog() {
  dialog.visible = false;
  roleFormRef.value.resetFields();
  roleFormRef.value.clearValidate();
  formData.id = undefined;
  Object.assign(formData, initialFormData);
}

// 打开角色权限设置弹窗
function handleOpenPermission(row: RolePageVO) {
  if (isRootRole(row)) {
    ElMessage.warning("ROOT 默认拥有全部路由权限，无需配置");
    return;
  }
  permissionDialog.roleId = row.id!;
  permissionDialog.visible = true;
  permissionDialog.loading = true;
  Promise.all([MenuAPI.getList(), RoleAPI.getRoleMenus(row.id!)])
    .then(([menus, checked]) => {
      menuTreeData.value = buildMenuTree(menus || []);
      permissionDialog.checkedKeys = checked || [];
    })
    .finally(() => (permissionDialog.loading = false));
}

// 提交角色权限（可访问路由）
function handlePermissionSubmit() {
  const checkedKeys = [
    ...(menuTreeRef.value?.getCheckedKeys() || []),
    ...(menuTreeRef.value?.getHalfCheckedKeys() || []),
  ];
  permissionDialog.loading = true;
  RoleAPI.assignRoleMenus(permissionDialog.roleId, checkedKeys)
    .then(() => {
      ElMessage.success("权限分配成功");
      permissionDialog.visible = false;
    })
    .finally(() => (permissionDialog.loading = false));
}

// 将扁平菜单列表转为 el-tree 所需的树结构（仅目录与菜单，排除按钮）
function buildMenuTree(menus: SysMenuVO[]): any[] {
  const nodes = (menus || []).filter((m) => m.menuType !== 2);
  const map = new Map<string, any>();
  const roots: any[] = [];
  nodes.forEach((m) => {
    map.set(m.id!, { id: m.id, label: m.menuName, children: [] });
  });
  nodes.forEach((m) => {
    const node = map.get(m.id!);
    const parent =
      m.parentId && m.parentId !== "0" ? map.get(m.parentId) : undefined;
    if (parent) parent.children.push(node);
    else roots.push(node);
  });
  return roots;
}

// 删除角色
function handleDelete(roleId?: string) {
  const selected = roleId ? [roleId] : ids.value;
  if (selected.length === 0) {
    ElMessage.warning("请勾选删除项");
    return;
  }
  const roleIds = selected.filter((id) => {
    const row = roleList.value.find((item) => item.id === id);
    return !isRootRole(row);
  });
  if (roleIds.length === 0) {
    ElMessage.warning("系统默认角色 ROOT 不允许删除");
    return;
  }

  ElMessageBox.confirm("确认删除已选中的数据项?", "警告", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  }).then(
    () => {
      loading.value = true;
      RoleAPI.deleteByIds(roleIds)
        .then(() => {
          ElMessage.success("删除成功");
          handleQuery();
        })
        .finally(() => (loading.value = false));
    },
    () => {
      ElMessage.info("已取消删除");
    }
  );
}

onMounted(() => {
  handleQuery();
});
</script>
