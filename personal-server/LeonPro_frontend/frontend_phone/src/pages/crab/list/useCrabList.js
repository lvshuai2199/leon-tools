/**
 * 蟹单列表的状态和操作（电脑版、手机版共用）。
 *
 * 写成纯 JS + 依赖注入（api、读写地址栏 query），这样可以直接用 node --test 做单元测试；
 * 类型见同目录 useCrabList.d.ts。
 *
 * 地址栏：单日 /crab?date=2026-09-29；区间 /crab?start=…&end=…；搜索 &q=…
 * 列表接口参数沿用原手机端：shipDate 或 shipDateStart/shipDateEnd，customerName / phone，current/size。
 */
import { computed, reactive, ref } from "vue";
import { shiftDay, todayStr } from "../../../utils/crab-parse.js";

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const PAGE_SIZE = 200;

function one(v) {
  return Array.isArray(v) ? v[0] : v;
}
function validDate(v) {
  const s = typeof one(v) === "string" ? one(v).trim() : "";
  return DATE_RE.test(s) ? s : "";
}

/** 地址栏 query → 筛选条件（非法日期回落到今天；区间起止颠倒时自动交换） */
export function parseListQuery(query = {}, today = todayStr()) {
  const date = validDate(query.date);
  const start = validDate(query.start);
  const end = validDate(query.end);
  const q = one(query.q);
  const keyword = typeof q === "string" ? q.trim() : "";
  if (start || end) {
    let s = start || end;
    let e = end || start;
    if (s > e) [s, e] = [e, s];
    return { mode: "range", date: s, start: s, end: e, keyword };
  }
  const d = date || today;
  return { mode: "day", date: d, start: d, end: d, keyword };
}

/** 筛选条件 → 地址栏 query */
export function buildListQuery(filter) {
  const query = filter.mode === "range" ? { start: filter.start, end: filter.end } : { date: filter.date };
  if (filter.keyword && filter.keyword.trim()) query.q = filter.keyword.trim();
  return query;
}

/** 筛选条件 → 列表接口参数（纯数字按电话搜，否则按姓名） */
export function buildApiParams(filter) {
  const params = { current: 1, size: PAGE_SIZE };
  if (filter.mode === "range") {
    params.shipDateStart = filter.start;
    params.shipDateEnd = filter.end;
  } else {
    params.shipDate = filter.date;
  }
  const key = (filter.keyword || "").trim();
  if (key) {
    if (/^\d+$/.test(key)) params.phone = key;
    else params.customerName = key;
  }
  return params;
}

/** 列表接口可能返回分页对象或数组 */
export function normalizeRecords(data) {
  if (Array.isArray(data)) return data;
  if (data && Array.isArray(data.records)) return data.records;
  return [];
}

/** 汇总：单数、未付、未发、合计只数 */
export function summarize(records) {
  const list = Array.isArray(records) ? records : [];
  return {
    count: list.length,
    unpaid: list.filter((r) => !r.paid).length,
    unshipped: list.filter((r) => !r.shipped).length,
    totalQty: list.reduce((sum, r) => sum + (Number(r.quantity) || 0), 0),
  };
}

/** 发货图、导出文件名里的日期文字 */
export function dateLabel(filter) {
  if (filter.mode === "range" && filter.start !== filter.end) return `${filter.start}至${filter.end}`;
  return filter.mode === "range" ? filter.start : filter.date;
}

/** 「录入」默认用哪天：单日用当天，区间用结束日 */
export function entryDate(filter) {
  return filter.mode === "range" ? filter.end || filter.start : filter.date;
}

/** 空状态文案 */
export function emptyText(filter) {
  if (filter.keyword) return `没有找到「${filter.keyword}」相关的出货单`;
  return filter.mode === "range" ? "这段时间还没有出货单" : "这一天还没有出货单";
}

function sameFilter(a, b) {
  return a.mode === b.mode && a.date === b.date && a.start === b.start && a.end === b.end && a.keyword === b.keyword;
}

/** 上次的结果（电脑上列表 ⇄ 详情弹窗切换路由时先显示旧数据，不闪白） */
const cache = new Map();

/**
 * @param {object} deps
 * @param {{ list: Function, updateStatus: Function, remove: Function }} deps.api
 * @param {() => Record<string, unknown>} deps.getQuery   读当前地址栏 query
 * @param {(q: Record<string, string>) => void} deps.setQuery  写地址栏（replace）
 * @param {string} [deps.today]
 */
export function useCrabList(deps) {
  const { api, getQuery, setQuery } = deps;
  const today = deps.today || todayStr();

  const filter = reactive(parseListQuery(getQuery(), today));
  const cacheKey = () => JSON.stringify(buildApiParams(filter));
  const records = ref(cache.get(cacheKey()) || []);
  const loading = ref(false);
  const loaded = ref(cache.has(cacheKey()));
  const error = ref("");

  const summary = computed(() => summarize(records.value));
  const label = computed(() => dateLabel(filter));
  const empty = computed(() => emptyText(filter));

  // 选择模式（导出发货图）
  const selecting = ref(false);
  const selectedIds = ref([]);
  const selectedRecords = computed(() => records.value.filter((r) => selectedIds.value.includes(r.id)));
  const allSelected = computed(() => records.value.length > 0 && selectedIds.value.length === records.value.length);

  let seq = 0;
  async function load() {
    const my = ++seq;
    const key = cacheKey();
    loading.value = true;
    error.value = "";
    try {
      const data = await api.list(buildApiParams(filter));
      if (my !== seq) return;
      records.value = normalizeRecords(data);
      cache.set(key, records.value);
      loaded.value = true;
      // 列表变了，去掉已经不在列表里的勾选
      selectedIds.value = selectedIds.value.filter((id) => records.value.some((r) => r.id === id));
    } catch (e) {
      if (my !== seq) return;
      error.value = (e && e.message) || "加载失败";
      records.value = [];
      loaded.value = true;
    } finally {
      if (my === seq) loading.value = false;
    }
  }

  function apply(patch) {
    Object.assign(filter, patch);
    if (filter.mode === "range" && filter.start && filter.end && filter.start > filter.end) {
      const s = filter.start;
      filter.start = filter.end;
      filter.end = s;
    }
    if (filter.mode === "day") {
      filter.start = filter.date;
      filter.end = filter.date;
    }
    setQuery(buildListQuery(filter));
    return load();
  }

  function setMode(mode) {
    if (mode === filter.mode) return Promise.resolve();
    if (mode === "range") return apply({ mode, start: filter.date, end: filter.date });
    return apply({ mode, date: filter.start || filter.date });
  }
  const setDate = (date) => apply({ mode: "day", date });
  const shift = (delta) => apply({ mode: "day", date: shiftDay(filter.date, delta) });
  const setRange = (start, end) => apply({ mode: "range", start: start || end, end: end || start });
  const search = (keyword) => apply({ keyword: String(keyword || "").trim() });

  /** 浏览器前进/后退改了地址栏时同步（和当前一样就不重新加载） */
  function syncFromQuery(query) {
    const next = parseListQuery(query, today);
    if (sameFilter(next, filter)) return Promise.resolve();
    Object.assign(filter, next);
    return load();
  }

  /** 点标签切换已付款/已发货：先改界面，失败再改回来 */
  async function toggleStatus(item, field) {
    const prev = item[field];
    const next = prev ? 0 : 1;
    item[field] = next;
    try {
      const updated = await api.updateStatus({ id: item.id, [field]: next });
      if (updated && typeof updated === "object") Object.assign(item, updated);
      return true;
    } catch (e) {
      item[field] = prev;
      return false;
    }
  }

  /** 扫到/识别到单号：写入并标记已发货 */
  async function applyTracking(item, code) {
    if (!code) return false;
    const updated = await api.updateStatus({ id: item.id, trackingNo: code, shipped: 1 });
    Object.assign(item, updated && typeof updated === "object" ? updated : { trackingNo: code, shipped: 1 });
    return true;
  }

  async function remove(item) {
    await api.remove([item.id]);
    records.value = records.value.filter((r) => r.id !== item.id);
    selectedIds.value = selectedIds.value.filter((id) => id !== item.id);
    cache.set(cacheKey(), records.value);
  }

  function toggleSelecting(on) {
    selecting.value = typeof on === "boolean" ? on : !selecting.value;
    if (!selecting.value) selectedIds.value = [];
  }
  function toggleItem(id) {
    const i = selectedIds.value.indexOf(id);
    if (i >= 0) selectedIds.value.splice(i, 1);
    else selectedIds.value.push(id);
  }
  function toggleAll() {
    selectedIds.value = allSelected.value ? [] : records.value.map((r) => r.id);
  }
  function setSelected(ids) {
    selectedIds.value = [...ids];
  }

  return {
    filter,
    records,
    loading,
    loaded,
    error,
    summary,
    label,
    empty,
    entryDate: () => entryDate(filter),
    listQuery: () => buildListQuery(filter),
    load,
    setMode,
    setDate,
    shift,
    setRange,
    search,
    syncFromQuery,
    toggleStatus,
    applyTracking,
    remove,
    selecting,
    selectedIds,
    selectedRecords,
    allSelected,
    toggleSelecting,
    toggleItem,
    toggleAll,
    setSelected,
  };
}

/** 测试用：清空缓存 */
export function _clearCrabListCache() {
  cache.clear();
}
