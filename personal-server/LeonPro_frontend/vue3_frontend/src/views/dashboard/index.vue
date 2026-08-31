<template>
  <div class="dashboard-container">
    <!-- 欢迎卡片 -->
    <el-card shadow="never" class="mt-2">
      <div class="welcome">
        <div class="welcome-left">
          <p class="greeting">{{ greetings }}</p>
          <p class="sub">
            欢迎使用 LeonTools 工作台 —— 工业机器人编程与控制工具集（ELITE / AUBO）
          </p>
        </div>
        <div class="welcome-right">
          <img v-if="userStore.userInfo.avatar" class="avatar" :src="userStore.userInfo.avatar" />
          <div class="user-meta">
            <span class="username">{{ userStore.userInfo.username }}</span>
            <span class="user-role">角色：{{ userStore.userInfo.roles?.join(" / ") }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 工具中心 -->
    <div class="section-title">工具中心</div>
    <el-row :gutter="16">
      <el-col v-for="tool in toolCards" :key="tool.path" :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="tool-card" @click="router.push(tool.path)">
          <div class="tool-card-body">
            <el-icon :size="36" :color="tool.color">
              <component :is="tool.icon" />
            </el-icon>
            <div class="tool-text">
              <span class="tool-name">{{ tool.title }}</span>
              <span class="tool-desc">{{ tool.desc }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 业务管理 -->
    <div class="section-title">业务管理</div>
    <el-row :gutter="16">
      <el-col v-for="tool in workCards" :key="tool.path" :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="tool-card" @click="router.push(tool.path)">
          <div class="tool-card-body">
            <el-icon :size="36" :color="tool.color">
              <component :is="tool.icon" />
            </el-icon>
            <div class="tool-text">
              <span class="tool-name">{{ tool.title }}</span>
              <span class="tool-desc">{{ tool.desc }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 系统管理 -->
    <div class="section-title">系统管理</div>
    <el-row :gutter="16">
      <el-col v-for="tool in systemCards" :key="tool.path" :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="tool-card" @click="router.push(tool.path)">
          <div class="tool-card-body">
            <el-icon :size="36" :color="tool.color">
              <component :is="tool.icon" />
            </el-icon>
            <div class="tool-text">
              <span class="tool-name">{{ tool.title }}</span>
              <span class="tool-desc">{{ tool.desc }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: "Dashboard",
  inheritAttrs: false,
});

import { markRaw } from "vue";
import { TrendCharts, Folder, Document, Tickets, Postcard, User, Avatar, Key, Setting } from "@element-plus/icons-vue";
import { useUserStore } from "@/store/modules/user";

const router = useRouter();
const userStore = useUserStore();

const currentDate = new Date();

const greetings = computed(() => {
  const hours = currentDate.getHours();
  const username = userStore.userInfo.username || "用户";
  if (hours >= 6 && hours < 12) {
    return `上午好，${username}！`;
  } else if (hours >= 12 && hours < 18) {
    return `下午好，${username}！`;
  } else if (hours >= 18 && hours < 24) {
    return `晚上好，${username}！`;
  }
  return `夜深了，${username}，注意休息！`;
});

interface ToolCard {
  title: string;
  desc: string;
  path: string;
  icon: any;
  color: string;
}

const toolCards = ref<ToolCard[]>([
  {
    title: "轨迹分析",
    desc: "独立页面解析 WeldingTools / FullFunctionWelding 工程，可视化任务树与机械臂轨迹",
    path: "/trace",
    icon: markRaw(TrendCharts),
    color: "#4080FF",
  },
  {
    title: "文件工具",
    desc: "批量改名、SHA-256 文件去重、正则筛选",
    path: "/tool/files",
    icon: markRaw(Folder),
    color: "#67C23A",
  },
  {
    title: "文档工具",
    desc: "PDF 合并、Markdown 编辑预览、图片引用检查",
    path: "/tool/documents",
    icon: markRaw(Document),
    color: "#FF9A2E",
  },
  {
    title: "注册码生成",
    desc: "按公司与名称生成临时注册码，结果写入操作日志",
    path: "/tool/regcode",
    icon: markRaw(Key),
    color: "#F76560",
  },
  {
    title: "注册码配置",
    desc: "公司、名称、组件、加密方式与后缀，后期在此增删",
    path: "/tool/regcode-config",
    icon: markRaw(Setting),
    color: "#9B59B6",
  },
]);

const workCards = ref<ToolCard[]>([
  {
    title: "任务管理",
    desc: "焊接任务派发与进度跟踪（对接后端 SysTasks）",
    path: "/work/tasks",
    icon: markRaw(Tickets),
    color: "#4080FF",
  },
  {
    title: "操作日志",
    desc: "注册码生成记录：公司、名称、操作人员与时间",
    path: "/work/registration",
    icon: markRaw(Postcard),
    color: "#F76560",
  },
]);

const systemCards = ref<ToolCard[]>([
  {
    title: "用户管理",
    desc: "系统用户的增删改查（对接后端 SysUsers）",
    path: "/system/user",
    icon: markRaw(User),
    color: "#67C23A",
  },
  {
    title: "角色管理",
    desc: "角色维护与路由权限（ROOT 默认全权限，不可配置）",
    path: "/system/role",
    icon: markRaw(Avatar),
    color: "#FF9A2E",
  },
]);
</script>

<style lang="scss" scoped>
.dashboard-container {
  padding: 24px;

  .welcome {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .greeting {
      font-size: 20px;
      font-weight: 600;
    }

    .sub {
      margin-top: 8px;
      font-size: 13px;
      color: var(--el-text-color-secondary);
    }

    .welcome-right {
      display: flex;
      align-items: center;

      .avatar {
        width: 64px;
        height: 64px;
        border-radius: 50%;
        object-fit: cover;
      }

      .user-meta {
        display: flex;
        flex-direction: column;
        margin-left: 12px;

        .username {
          font-size: 16px;
          font-weight: 600;
        }

        .user-role {
          margin-top: 4px;
          font-size: 12px;
          color: var(--el-text-color-secondary);
        }
      }
    }
  }

  .section-title {
    margin: 20px 0 12px;
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .tool-card {
    margin-bottom: 16px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      transform: translateY(-4px);
    }

    .tool-card-body {
      display: flex;
      align-items: center;

      .tool-text {
        display: flex;
        flex-direction: column;
        margin-left: 16px;

        .tool-name {
          font-size: 15px;
          font-weight: 600;
        }

        .tool-desc {
          margin-top: 6px;
          font-size: 12px;
          color: var(--el-text-color-secondary);
        }
      }
    }
  }
}
</style>
