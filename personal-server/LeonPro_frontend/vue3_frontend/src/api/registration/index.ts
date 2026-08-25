import request from "@/utils/request";

const REG_BASE_URL = "/comRegistration";

/**
 * 注册申请 API（对接 LeonPro_backend ComRegistrationController）
 */
const RegistrationAPI = {
  /** 获取注册申请分页列表 */
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
      },
    });
  },

  /** 获取详情 */
  getFormData(id: string) {
    return request<any, RegistrationForm>({
      url: `${REG_BASE_URL}/${id}`,
      method: "get",
    });
  },

  /** 新增注册申请（后端自动写一条 SysInfo 通知） */
  add(data: RegistrationForm) {
    return request<any, string>({
      url: `${REG_BASE_URL}/add`,
      method: "post",
      data,
    });
  },

  /** 修改注册申请 */
  update(data: RegistrationForm) {
    return request<any, boolean>({
      url: `${REG_BASE_URL}/update`,
      method: "post",
      data,
    });
  },

  /** 批量删除 */
  deleteByIds(ids: string[]) {
    return request<any, boolean>({
      url: `${REG_BASE_URL}/del`,
      method: "post",
      params: { idList: ids.join(",") },
    });
  },

  /**
   * 根据注册申请生成正式注册码（置 applyStatus=1）
   */
  getRegCode(data: RegistrationForm) {
    return request<any, RegistrationForm>({
      url: `/auth/getRegCode`,
      method: "post",
      data,
    });
  },

  /**
   * 临时生成多种有效期注册码（1/2/4/6/13个月/永久）
   */
  genTempRegCode(applyName: string, company: string) {
    return request<any, TempRegCodeVO>({
      url: `/auth/genTempRegCode`,
      method: "post",
      data: { applyName, company },
    });
  },
};

export default RegistrationAPI;

/** 注册申请分页查询参数 */
export interface RegistrationPageQuery extends PageQuery {
  applyName?: string;
  company?: string;
  applyPhone?: string;
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
