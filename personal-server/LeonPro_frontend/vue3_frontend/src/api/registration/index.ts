import request from "@/utils/request";

const REG_BASE_URL = "/admin/comRegistration";

/**
 * 注册码操作日志 API（对接 LeonPro_backend ComRegistrationController）
 */
const RegistrationAPI = {
  /** 获取操作日志分页列表 */
  getPage(queryParams: RegistrationPageQuery) {
    return request<any, IPageResult<RegistrationPageVO>>({
      url: `${REG_BASE_URL}/getAll`,
      method: "get",
      params: {
        current: queryParams.current ?? 1,
        size: queryParams.size ?? 10,
        applyName: queryParams.applyName || undefined,
        company: queryParams.company || undefined,
        applyPhone: queryParams.applyPhone || undefined,
        applyStatus: queryParams.applyStatus,
        operator: queryParams.operator || undefined,
      },
    });
  },

  /**
   * 临时生成多种有效期注册码（1/2/4/6/13个月/永久）
   * applyId 为当前操作人用户 ID，空则后端记为「未知人员」
   */
  genTempRegCode(data: TempRegCodeForm) {
    return request<any, TempRegCodeVO>({
      url: `/common/regCode/genTempRegCode`,
      method: "post",
      data,
    });
  },
};

export default RegistrationAPI;

/** 注册申请分页查询参数 */
export interface RegistrationPageQuery extends PageQuery {
  applyName?: string;
  company?: string;
  applyPhone?: string;
  /** 操作人员（用户 ID / 未知人员） */
  operator?: string;
  /** 0-待处理 1-已生成注册码 */
  applyStatus?: number;
}

/** 注册申请分页对象（ComRegistration 映射） */
export interface RegistrationPageVO {
  id?: string;
  applyName?: string;
  company?: string;
  salesName?: string;
  applyPhone?: string;
  regCode?: string;
  regCodeType?: number;
  remarks?: string;
  oneMonthValid?: string;
  longTimeValid?: string;
  applyId?: string;
  /** 操作人员（用户 ID；无则「未知人员」） */
  operator?: string;
  createTime?: string;
  applyStatus?: number;
}

/** 注册申请表单 */
export interface RegistrationForm {
  id?: string;
  applyName?: string;
  company?: string;
  salesName?: string;
  applyPhone?: string;
  regCode?: string;
  regCodeType?: number;
  remarks?: string;
  applyStatus?: number;
  operator?: string;
}

/** 临时注册码生成请求 */
export interface TempRegCodeForm {
  regCode?: string;
  regCodeType?: number;
  /** 注册码配置 ID（PC 生成页走配置） */
  configId?: string;
  applyName?: string;
  company?: string;
  /** 当前操作人用户 ID */
  applyId?: string;
}

/** 临时注册码生成结果（RegCode DTO 映射） */
export interface TempRegCodeVO {
  regCode?: string;
  regCodeType?: number;
  oneMonthValid?: string;
  twoMonthValid?: string;
  fourMonthValid?: string;
  sixMonthValid?: string;
  thirteenMonthValid?: string;
  longTimeValid?: string;
}
