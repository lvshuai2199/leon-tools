import { test } from "node:test";
import assert from "node:assert/strict";
import { nextTick } from "vue";
import {
  _clearCrabListCache,
  buildApiParams,
  buildListQuery,
  dateLabel,
  emptyText,
  entryDate,
  parseListQuery,
  summarize,
  useCrabList,
} from "./useCrabList.js";

const TODAY = "2026-09-29";

function fakeApi(rows = []) {
  const calls = { list: [], status: [], remove: [] };
  return {
    calls,
    rows,
    failStatus: false,
    async list(params) {
      calls.list.push(params);
      return { records: this.rows.map((r) => ({ ...r })), total: this.rows.length };
    },
    async updateStatus(data) {
      calls.status.push(data);
      if (this.failStatus) throw new Error("网络错误");
      return { ...data, updated: true };
    },
    async remove(ids) {
      calls.remove.push(ids);
      return ids.length;
    },
  };
}

function setup(query = {}, rows) {
  _clearCrabListCache();
  const api = fakeApi(rows);
  const written = [];
  const list = useCrabList({ api, getQuery: () => query, setQuery: (q) => written.push(q), today: TODAY });
  return { api, list, written };
}

test("parseListQuery：默认今天、单日、区间、非法日期、起止颠倒", () => {
  assert.deepEqual(parseListQuery({}, TODAY), { mode: "day", date: TODAY, start: TODAY, end: TODAY, keyword: "" });
  assert.equal(parseListQuery({ date: "2026-09-01" }, TODAY).date, "2026-09-01");
  assert.equal(parseListQuery({ date: "abc" }, TODAY).date, TODAY);
  const r = parseListQuery({ start: "2026-09-20", end: "2026-09-10", q: " 张 " }, TODAY);
  assert.deepEqual(r, { mode: "range", date: "2026-09-10", start: "2026-09-10", end: "2026-09-20", keyword: "张" });
  // 只给 start：结束日等于开始日
  assert.deepEqual(parseListQuery({ start: "2026-09-05" }, TODAY).end, "2026-09-05");
  // query 值是数组（vue-router 重复参数）
  assert.equal(parseListQuery({ date: ["2026-09-02", "x"] }, TODAY).date, "2026-09-02");
});

test("buildListQuery / buildApiParams：电话按数字搜，姓名按文字搜", () => {
  const day = { mode: "day", date: "2026-09-29", start: "2026-09-29", end: "2026-09-29", keyword: "" };
  assert.deepEqual(buildListQuery(day), { date: "2026-09-29" });
  assert.deepEqual(buildApiParams(day), { current: 1, size: 200, shipDate: "2026-09-29" });
  const range = { mode: "range", date: "", start: "2026-09-01", end: "2026-09-29", keyword: "138" };
  assert.deepEqual(buildListQuery(range), { start: "2026-09-01", end: "2026-09-29", q: "138" });
  assert.deepEqual(buildApiParams(range), { current: 1, size: 200, shipDateStart: "2026-09-01", shipDateEnd: "2026-09-29", phone: "138" });
  assert.equal(buildApiParams({ ...day, keyword: "张三" }).customerName, "张三");
});

test("summarize / dateLabel / entryDate / emptyText", () => {
  const s = summarize([
    { paid: 1, shipped: 0, quantity: 10 },
    { paid: 0, shipped: 1, quantity: "6" },
    { paid: true, shipped: true, quantity: null },
  ]);
  assert.deepEqual(s, { count: 3, unpaid: 1, unshipped: 1, totalQty: 16 });
  const range = { mode: "range", date: "2026-09-01", start: "2026-09-01", end: "2026-09-03", keyword: "" };
  assert.equal(dateLabel(range), "2026-09-01至2026-09-03");
  assert.equal(entryDate(range), "2026-09-03");
  assert.equal(emptyText(range), "这段时间还没有出货单");
  assert.equal(emptyText({ ...range, keyword: "王" }), "没有找到「王」相关的出货单");
});

test("useCrabList：加载、切换日期写回地址栏", async () => {
  const { api, list, written } = setup({ date: "2026-09-28" }, [{ id: "a", paid: 0, shipped: 0, quantity: 3 }]);
  await list.load();
  assert.equal(list.records.value.length, 1);
  assert.equal(api.calls.list[0].shipDate, "2026-09-28");
  await list.shift(1);
  assert.deepEqual(written.at(-1), { date: "2026-09-29" });
  await list.setMode("range");
  assert.deepEqual(written.at(-1), { start: "2026-09-29", end: "2026-09-29" });
  await list.setRange("2026-09-30", "2026-09-01");
  assert.deepEqual(written.at(-1), { start: "2026-09-01", end: "2026-09-30" });
  await list.search(" 13800 ");
  assert.equal(api.calls.list.at(-1).phone, "13800");
  assert.equal(list.summary.value.totalQty, 3);
});

test("useCrabList：同样的地址栏不重复加载；不同才加载", async () => {
  const { api, list } = setup({ date: "2026-09-28" }, []);
  await list.load();
  await list.syncFromQuery({ date: "2026-09-28" });
  assert.equal(api.calls.list.length, 1);
  await list.syncFromQuery({ start: "2026-09-01", end: "2026-09-02" });
  assert.equal(api.calls.list.length, 2);
  assert.equal(list.filter.mode, "range");
});

test("useCrabList：切换状态先改界面，失败回滚", async () => {
  const { api, list } = setup({}, [{ id: "a", paid: 0, shipped: 0 }]);
  await list.load();
  const item = list.records.value[0];
  assert.equal(await list.toggleStatus(item, "paid"), true);
  assert.equal(item.paid, 1);
  assert.deepEqual(api.calls.status[0], { id: "a", paid: 1 });
  api.failStatus = true;
  assert.equal(await list.toggleStatus(item, "shipped"), false);
  assert.equal(item.shipped, 0);
});

test("useCrabList：写单号自动标记已发货；删除从列表去掉", async () => {
  const { api, list } = setup({}, [{ id: "a", paid: 0, shipped: 0 }, { id: "b", paid: 0, shipped: 0 }]);
  await list.load();
  const item = list.records.value[0];
  assert.equal(await list.applyTracking(item, ""), false);
  assert.equal(await list.applyTracking(item, "SF123"), true);
  assert.deepEqual(api.calls.status.at(-1), { id: "a", trackingNo: "SF123", shipped: 1 });
  assert.equal(item.shipped, 1);
  list.toggleSelecting(true);
  list.toggleItem("b");
  await list.remove(list.records.value[1]);
  assert.deepEqual(api.calls.remove[0], ["b"]);
  assert.equal(list.records.value.length, 1);
  assert.deepEqual(list.selectedIds.value, []);
});

test("useCrabList：选择模式全选/取消；加载失败给出错误", async () => {
  const { api, list } = setup({}, [{ id: "a" }, { id: "b" }]);
  await list.load();
  list.toggleSelecting();
  list.toggleAll();
  assert.equal(list.allSelected.value, true);
  assert.equal(list.selectedRecords.value.length, 2);
  list.toggleAll();
  assert.equal(list.selectedIds.value.length, 0);
  list.toggleSelecting(false);
  assert.equal(list.selecting.value, false);
  api.list = async () => {
    throw new Error("服务器开小差");
  };
  await list.load();
  await nextTick();
  assert.equal(list.error.value, "服务器开小差");
  assert.equal(list.records.value.length, 0);
});

test("useCrabList：后发起的请求才生效（快速切日期不串数据）", async () => {
  _clearCrabListCache();
  const resolvers = [];
  const api = {
    list: (params) => new Promise((r) => resolvers.push(() => r([{ id: params.shipDate }]))),
    updateStatus: async () => null,
    remove: async () => 0,
  };
  const list = useCrabList({ api, getQuery: () => ({}), setQuery: () => {}, today: TODAY });
  const p1 = list.setDate("2026-09-01");
  const p2 = list.setDate("2026-09-02");
  resolvers[1]();
  await p2;
  resolvers[0]();
  await p1;
  assert.equal(list.records.value[0].id, "2026-09-02");
  assert.equal(list.loading.value, false);
});
