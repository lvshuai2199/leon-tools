import request from "@/utils/request";

const TASK_BASE_URL = "/sysTasks";

/**
 * 任务 API（对接 LeonPro_backend SysTasksController）
 *
 * 注意：getAll 必须传 publisherId，否则后端返回"请先登录"
 */
const TaskAPI = {
  /**
   * 获取任务分页列表
   * GET /sysTasks/getAll?current=1&size=10&publisherId=xxx&...
   */
  getPage(queryParams: TaskPageQuery) {
    return request<any, IPageResult<TaskPageVO>>({
      url: `${TASK_BASE_URL}/getAll`,
      method: "get",
      params: {
        current: queryParams.current ?? 1,
        size: queryParams.size ?? 10,
        publisherId: queryParams.publisherId,
        taskName: queryParams.taskName || undefined,
        taskType: queryParams.taskType || undefined,
        taskLevel: queryParams.taskLevel || undefined,
        taskStatus: queryParams.taskStatus || undefined,
        handlerId: queryParams.handlerId || undefined,
        customerName: queryParams.customerName || undefined,
        startTime: queryParams.startTime || undefined,
        endTime: queryParams.endTime || undefined,
      },
    });
  },

  /** 获取任务详情 */
  getFormData(id: string) {
    return request<any, TaskForm>({
      url: `${TASK_BASE_URL}/${id}`,
      method: "get",
    });
  },

  /** 新增任务（自动设置 createTime 与 isDelete=0） */
  add(data: TaskForm) {
    return request<any, boolean>({
      url: `${TASK_BASE_URL}/add`,
      method: "post",
      data,
    });
  },

  /** 修改任务 */
  update(data: TaskForm) {
    return request<any, boolean>({
      url: `${TASK_BASE_URL}/update`,
      method: "post",
      data,
    });
  },

  /**
   * 异常上报（追加 remarks 并以 END 结尾）
   */
  abnormalUpload(data: TaskForm) {
    return request<any, TaskForm>({
      url: `/auth/abnormalUpload`,
      method: "post",
      data,
    });
  },

  /** 批量删除任务 */
  deleteByIds(ids: string[]) {
    return request<any, boolean>({
      url: `${TASK_BASE_URL}/del`,
      method: "delete",
      params: { idList: ids.join(",") },
    });
  },
};

export default TaskAPI;

/** 任务分页查询参数（对应后端 TaskDto） */
export interface TaskPageQuery extends PageQuery {
  /** 发布者ID（必传） */
  publisherId?: string;
  /** 任务名称（模糊查询） */
  taskName?: string;
  /** 任务类别 */
  taskType?: string;
  /** 任务等级 */
  taskLevel?: string;
  /** 任务状态 */
  taskStatus?: string;
  /** 处理人 */
  handlerId?: string;
  /** 客户名称 */
  customerName?: string;
  /** 开始时间 yyyy-MM-dd */
  startTime?: string;
  /** 结束时间 yyyy-MM-dd */
  endTime?: string;
}

/** 任务分页对象（SysTasks 映射） */
export interface TaskPageVO {
  id?: string;
  taskType?: string;
  taskName?: string;
  description?: string;
  taskLevel?: string;
  taskStatus?: string;
  publisherId?: string;
  handlerId?: string;
  createTime?: string;
  updateTime?: string;
  customerName?: string;
  customerPlace?: string;
  industry?: string;
  scenario?: string;
  robotType?: string;
  robotNum?: string;
  remarks?: string;
  isDelete?: number;
}

/** 任务表单 */
export interface TaskForm {
  id?: string;
  taskType?: string;
  taskName?: string;
  description?: string;
  taskLevel?: string;
  taskStatus?: string;
  publisherId?: string;
  handlerId?: string;
  customerName?: string;
  customerPlace?: string;
  industry?: string;
  scenario?: string;
  robotType?: string;
  robotNum?: string;
  remarks?: string;
  isDelete?: number;
}
