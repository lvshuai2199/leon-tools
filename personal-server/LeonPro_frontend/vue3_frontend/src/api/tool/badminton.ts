import request from "@/utils/request";

const BASE_URL = "/admin/badmintonBill";

const BadmintonBillAPI = {
  getPage(queryParams: BadmintonBillPageQuery) {
    return request<any, IPageResult<BadmintonBillVO>>({
      url: `${BASE_URL}/getAll`,
      method: "get",
      params: {
        current: queryParams.current ?? 1,
        size: queryParams.size ?? 10,
        playDate: queryParams.playDateStart || queryParams.playDateEnd ? undefined : queryParams.playDate || undefined,
        playDateStart: queryParams.playDateStart || undefined,
        playDateEnd: queryParams.playDateEnd || undefined,
        title: queryParams.title || undefined,
      },
    });
  },

  getById(id: string) {
    return request<any, BadmintonBillVO>({
      url: `${BASE_URL}/${id}`,
      method: "get",
    });
  },

  save(data: BadmintonBillForm) {
    return request<any, BadmintonBillVO>({
      url: `${BASE_URL}/save`,
      method: "post",
      data,
    });
  },

  preview(data: BadmintonBillForm) {
    return request<any, BadmintonBillVO>({
      url: `${BASE_URL}/preview`,
      method: "post",
      data,
    });
  },

  deleteByIds(ids: string[]) {
    return request<any, boolean>({
      url: `${BASE_URL}/del`,
      method: "post",
      data: ids,
    });
  },
};

export default BadmintonBillAPI;

export interface BadmintonBillPageQuery extends PageQuery {
  playDate?: string;
  playDateStart?: string;
  playDateEnd?: string;
  title?: string;
}

export interface BadmintonCourtFeeItem {
  id?: string;
  courtCount?: number | null;
  hours?: number | null;
  unitPrice?: number | null;
  amount?: number | null;
  remark?: string;
}

export interface BadmintonBallFeeItem {
  id?: string;
  brand?: string;
  quantity?: number | null;
  unitPrice?: number | null;
  amount?: number | null;
}

export interface BadmintonBillVO {
  id?: string;
  playDate?: string;
  title?: string;
  participantCount?: number;
  courtTotal?: number;
  ballTotal?: number;
  grandTotal?: number;
  perPerson?: number;
  remark?: string;
  operatorName?: string;
  createTime?: string;
  updateTime?: string;
  courtItems?: BadmintonCourtFeeItem[];
  ballItems?: BadmintonBallFeeItem[];
}

export interface BadmintonBillForm {
  id?: string;
  playDate?: string;
  title?: string;
  participantCount?: number | null;
  remark?: string;
  courtItems?: BadmintonCourtFeeItem[];
  ballItems?: BadmintonBallFeeItem[];
}

export function money(value: number | string | null | undefined): number {
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0) return 0;
  return Math.round(n * 100) / 100;
}

export function intVal(value: number | string | null | undefined): number {
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0) return 0;
  return Math.floor(n);
}

export function courtAmount(item: BadmintonCourtFeeItem): number {
  return money(intVal(item.courtCount) * money(item.hours) * money(item.unitPrice));
}

export function ballAmount(item: BadmintonBallFeeItem): number {
  return money(intVal(item.quantity) * money(item.unitPrice));
}

export function summarize(form: {
  participantCount?: number | null;
  courtItems?: BadmintonCourtFeeItem[];
  ballItems?: BadmintonBallFeeItem[];
}) {
  const courtTotal = money((form.courtItems || []).reduce((sum, item) => sum + courtAmount(item), 0));
  const ballTotal = money((form.ballItems || []).reduce((sum, item) => sum + ballAmount(item), 0));
  const grandTotal = money(courtTotal + ballTotal);
  const people = Math.max(1, intVal(form.participantCount) || 1);
  const perPerson = money(grandTotal / people);
  return { courtTotal, ballTotal, grandTotal, perPerson, people };
}

export function formatMoney(value: number | string | null | undefined): string {
  return money(value).toFixed(2);
}

export function emptyCourt(): BadmintonCourtFeeItem {
  return { courtCount: 1, hours: 2, unitPrice: 0, remark: "" };
}

export function emptyBall(): BadmintonBallFeeItem {
  return { brand: "", quantity: 1, unitPrice: 0 };
}

export function buildSummaryText(form: BadmintonBillForm & ReturnType<typeof summarize>): string {
  const lines: string[] = [];
  const head = [form.playDate, form.title].filter(Boolean).join(" ");
  lines.push(head ? `羽毛球计费 ${head}` : "羽毛球计费");
  lines.push("场地费：");
  const courts = form.courtItems || [];
  if (!courts.length) {
    lines.push("  无");
  } else {
    courts.forEach((item, i) => {
      const note = item.remark ? `（${item.remark}）` : "";
      lines.push(
        `  ${i + 1}. ${intVal(item.courtCount)}片 × ${formatMoney(item.hours)}小时 × ${formatMoney(item.unitPrice)}元 = ${formatMoney(courtAmount(item))}元${note}`
      );
    });
  }
  lines.push("用球费用：");
  const balls = form.ballItems || [];
  if (!balls.length) {
    lines.push("  无");
  } else {
    balls.forEach((item, i) => {
      const brand = item.brand?.trim() || "未填品牌";
      lines.push(
        `  ${i + 1}. ${brand} × ${intVal(item.quantity)} × ${formatMoney(item.unitPrice)}元 = ${formatMoney(ballAmount(item))}元`
      );
    });
  }
  lines.push(`人数：${form.people}人`);
  lines.push(`场地合计：${formatMoney(form.courtTotal)}元`);
  lines.push(`用球合计：${formatMoney(form.ballTotal)}元`);
  lines.push(`总计：${formatMoney(form.grandTotal)}元`);
  lines.push(`个人应付：${formatMoney(form.perPerson)}元`);
  return lines.join("\n");
}
