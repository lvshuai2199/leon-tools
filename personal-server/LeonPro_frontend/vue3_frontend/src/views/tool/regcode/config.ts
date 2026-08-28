/** 有效期字段（与后端 RegCode DTO 对齐） */
export type ValidityKey =
  | "oneMonthValid"
  | "twoMonthValid"
  | "fourMonthValid"
  | "sixMonthValid"
  | "thirteenMonthValid"
  | "longTimeValid";

export const VALIDITY_LABELS: Record<ValidityKey, string> = {
  oneMonthValid: "一个月",
  twoMonthValid: "两个月",
  fourMonthValid: "四个月",
  sixMonthValid: "六个月",
  thirteenMonthValid: "十三个月",
  longTimeValid: "永久",
};

/** 默认展示前两项，其余折叠 */
export const DEFAULT_VISIBLE_FIELDS: ValidityKey[] = ["oneMonthValid", "longTimeValid"];

export const ALL_VALIDITY_FIELDS: ValidityKey[] = [
  "oneMonthValid",
  "twoMonthValid",
  "fourMonthValid",
  "sixMonthValid",
  "thirteenMonthValid",
  "longTimeValid",
];
