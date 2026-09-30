<template>
  <div :class="['navbar__right', navbarRightClass]">
    <!-- 桌面端显示 -->
    <template v-if="isDesktop">
      <!-- 搜索 -->
      <MenuSearch />

      <!-- 全屏 -->
      <Fullscreen />

      <!-- 布局大小 -->
      <SizeSelect />

      <!-- 语言选择 -->
      <LangSelect />
    </template>

    <!-- 用户头像（个人中心、注销登录等） -->
    <el-dropdown trigger="click">
      <div class="user-profile">
        <img class="user-profile__avatar" :src="userStore.userInfo.avatar" />
        <span class="user-profile__name">{{ userStore.userInfo.username }}</span>
      </div>
      <template #dropdown>
        <el-dropdown-menu>
          <!-- 窄屏隐藏了顶栏按钮，字号和语言挪进这里 -->
          <template v-if="!isDesktop">
            <el-dropdown-item disabled class="navbar__menu-label">
              {{ $t("sizeSelect.tooltip") }}
            </el-dropdown-item>
            <el-dropdown-item
              v-for="item in sizeOptions"
              :key="item.value"
              :class="{ 'navbar__menu-current': appStore.size === item.value }"
              @click="handleSizeChange(item.value)"
            >
              {{ item.label }}
            </el-dropdown-item>
            <el-dropdown-item disabled divided class="navbar__menu-label">
              语言 / Language
            </el-dropdown-item>
            <el-dropdown-item
              v-for="item in langOptions"
              :key="item.value"
              :class="{ 'navbar__menu-current': appStore.language === item.value }"
              @click="handleLanguageChange(item.value)"
            >
              {{ item.label }}
            </el-dropdown-item>
          </template>
          <el-dropdown-item :divided="!isDesktop" @click="handleProfileClick">
            {{ $t("navbar.profile") }}
          </el-dropdown-item>
          <el-dropdown-item divided @click="logout">
            {{ $t("navbar.logout") }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <!-- 设置面板 -->
    <div v-if="defaultSettings.showSettings" @click="settingStore.settingsVisible = true">
      <div class="i-svg:setting" />
    </div>
  </div>
</template>
<script setup lang="ts">
import defaultSettings from "@/settings";
import { DeviceEnum } from "@/enums/DeviceEnum";
import { useAppStore, useSettingsStore, useUserStore, useTagsViewStore } from "@/store";

import { SidebarColorEnum, ThemeEnum } from "@/enums/ThemeEnum";
import { SizeEnum } from "@/enums/SizeEnum";
import { LanguageEnum } from "@/enums/LanguageEnum";

const appStore = useAppStore();
const settingStore = useSettingsStore();
const userStore = useUserStore();
const tagsViewStore = useTagsViewStore();

const route = useRoute();
const router = useRouter();
const isDesktop = computed(() => appStore.device === DeviceEnum.DESKTOP);

const { locale, t } = useI18n();
const sizeOptions = computed(() => [
  { label: t("sizeSelect.default"), value: SizeEnum.DEFAULT },
  { label: t("sizeSelect.large"), value: SizeEnum.LARGE },
  { label: t("sizeSelect.small"), value: SizeEnum.SMALL },
]);
const langOptions = [
  { label: "中文", value: LanguageEnum.ZH_CN },
  { label: "English", value: LanguageEnum.EN },
];

function handleSizeChange(size: string) {
  appStore.changeSize(size);
  ElMessage.success(t("sizeSelect.message.success"));
}

function handleLanguageChange(lang: string) {
  locale.value = lang;
  appStore.changeLanguage(lang);
  ElMessage.success(t("langSelect.message.success"));
}

/**
 * 打开个人中心页面
 */
function handleProfileClick() {
  router.push({ name: "Profile" });
}

// 根据主题和侧边栏配色方案选择 navbar 右侧的样式类
const navbarRightClass = computed(() => {
  // 如果暗黑主题
  if (settingStore.theme === ThemeEnum.DARK) {
    return "navbar__right--white";
  }

  // 如果侧边栏是经典蓝

  if (settingStore.sidebarColorScheme === SidebarColorEnum.CLASSIC_BLUE) {
    return "navbar__right--white";
  }
});

/**
 * 注销登出
 */
function logout() {
  ElMessageBox.confirm("确定注销并退出系统吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    lockScroll: false,
  }).then(() => {
    userStore
      .logout()
      .then(() => {
        tagsViewStore.delAllViews();
      })
      .then(() => {
        router.replace({ path: "/login" });
      });
  });
}
</script>

<style lang="scss" scoped>
.navbar__right {
  display: flex;
  align-items: center;
  justify-content: center;

  & > * {
    display: inline-block;
    min-width: 40px;
    height: $navbar-height;
    line-height: $navbar-height;
    color: var(--el-text-color);
    text-align: center;
    cursor: pointer;

    &:hover {
      background: rgb(0 0 0 / 10%);
    }
  }
  .user-profile {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    padding: 0 13px;

    &__avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
    }

    &__name {
      margin-left: 10px;
    }
  }
}

.layout-top .navbar__right--white > *,
.layout-mix .navbar__right--white > * {
  color: #fff;
}

.dark .navbar__right > *:hover {
  color: #ccc;
}
</style>
