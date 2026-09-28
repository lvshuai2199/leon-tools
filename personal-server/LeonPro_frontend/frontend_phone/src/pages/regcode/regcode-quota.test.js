import { test } from "node:test";
import assert from "node:assert/strict";
import {
  adjustRows,
  buildCreateQuotas,
  buildDeltas,
  createQuotaRows,
  isExhausted,
  limitState,
  maxAdd,
  maxRevoke,
  needsSubUserListCheck,
  refundableTotal,
  remainingForConfig,
  showSubUserTab,
  sortSubUsers,
  sumQuota,
  usageText,
  validateSubUserForm,
} from "./regcode-quota.js";

const top = { isSubUser: false, canCreateSubUsers: true, canManageSubUsers: true, maxSubUsers: 3, createdCount: 2 };

test("标签页显隐：子用户、不能管理、上限 0 且没建过都不显示", () => {
  assert.equal(showSubUserTab(top), true);
  assert.equal(showSubUserTab(null), false);
  assert.equal(showSubUserTab({ ...top, isSubUser: true }), false);
  assert.equal(showSubUserTab({ ...top, canManageSubUsers: false }), false);
  const zero = { ...top, maxSubUsers: 0, createdCount: 0, canCreateSubUsers: false };
  assert.equal(needsSubUserListCheck(zero), true);
  assert.equal(showSubUserTab(zero), false);
  assert.equal(showSubUserTab(zero, 0), false);
  // 上限 0 但以前建过（现在都停用了）→ 仍显示，才能管理那些停用的
  assert.equal(showSubUserTab(zero, 2), true);
  // 上限调成 0 但还有启用中的 → 显示
  assert.equal(needsSubUserListCheck({ ...zero, createdCount: 1 }), false);
  assert.equal(showSubUserTab({ ...zero, createdCount: 1 }), true);
});

test("已建数 / 上限：未满、建满、超限", () => {
  assert.deepEqual(limitState(1, 3), { text: "已建 1 / 最多 3 个", over: false, full: false, canCreate: true });
  assert.deepEqual(limitState(3, 3), { text: "已建 3 / 最多 3 个", over: false, full: true, canCreate: false });
  assert.deepEqual(limitState(5, 3), { text: "已建 5 / 最多 3 个", over: true, full: true, canCreate: false });
});

test("合计与用完判断", () => {
  assert.deepEqual(sumQuota([{ used: 2, allocated: 10 }, { used: 5, allocated: 5 }]), { used: 7, allocated: 15, remaining: 8 });
  assert.deepEqual(sumQuota(undefined), { used: 0, allocated: 0, remaining: 0 });
  assert.equal(isExhausted(12, 50), false);
  assert.equal(isExhausted(5, 5), true);
  assert.equal(isExhausted(0, 0), true);
  assert.equal(usageText(12, 50), "12 / 50");
});

test("列表排序：停用的排最后，其余保持原顺序", () => {
  const list = [
    { id: "a", status: 0 },
    { id: "b", status: 1 },
    { id: "c", status: 1 },
    { id: "d", status: 0 },
  ];
  assert.deepEqual(sortSubUsers(list).map((u) => u.id), ["b", "c", "a", "d"]);
});

test("生成页剩余：按配置、不限、旧返回", () => {
  const q = { unlimited: false, items: [{ configId: "cfg1", used: 3, allocated: 10, remaining: 7 }], remaining: 7 };
  assert.equal(remainingForConfig(q, "cfg1"), 7);
  assert.equal(remainingForConfig(q, "cfg9"), 0);
  assert.equal(remainingForConfig({ unlimited: true, items: [] }, "cfg1"), null);
  assert.equal(remainingForConfig({ unlimited: false, items: [], remaining: 4 }, "x"), 4);
  assert.equal(remainingForConfig(null, "x"), null);
});

test("新建子用户：分配行、超剩余报错、只带大于 0 的", () => {
  const rows = createQuotaRows({ unlimited: false, items: [
    { configId: "cfg1", configName: "基础版", allocated: 10, used: 3, remaining: 7 },
    { configId: "cfg2", configName: "专业版", allocated: 5, used: 5, remaining: 0 },
  ] });
  assert.deepEqual(rows, [
    { configId: "cfg1", configName: "基础版", remaining: 7 },
    { configId: "cfg2", configName: "专业版", remaining: 0 },
  ]);
  assert.deepEqual(buildCreateQuotas(rows, { cfg1: 5, cfg2: 0 }), { quotas: [{ configId: "cfg1", count: 5 }], error: "" });
  assert.equal(buildCreateQuotas(rows, { cfg1: 8 }).error, "「基础版」你只剩 7 次");
  assert.deepEqual(buildCreateQuotas(rows, {}).quotas, []);
  // 不限次数（ROOT）：行来自配置列表，不限上限
  const unl = createQuotaRows({ unlimited: true, items: [] }, [{ id: 1, name: "A" }]);
  assert.deepEqual(unl, [{ configId: "1", configName: "A", remaining: null }]);
  assert.deepEqual(buildCreateQuotas(unl, { 1: 999 }).quotas, [{ configId: "1", count: 999 }]);
});

test("新建表单校验", () => {
  assert.equal(validateSubUserForm({ username: "ab", password: "123456" }), "账号需要 3–20 位");
  assert.equal(validateSubUserForm({ username: "a b c", password: "123456" }), "账号只能用字母、数字和 _ . @ -");
  assert.equal(validateSubUserForm({ username: "shop01", password: "123" }), "初始密码至少 6 位");
  assert.equal(validateSubUserForm({ username: "shop01", password: "123456" }), "");
});

test("额度调整：合并行、追加上限 = 我的剩余、收回上限 = 未用", () => {
  const quota = {
    subUserId: "s1",
    items: [{ configId: "cfg1", configName: "基础版", allocated: 10, used: 2, remaining: 8 }],
    refundableTotal: 8,
    creatorRemaining: [
      { configId: "cfg1", configName: "基础版", allocated: 20, used: 13, remaining: 7 },
      { configId: "cfg3", configName: "标准版", allocated: 20, used: 2, remaining: 18 },
    ],
  };
  const rows = adjustRows(quota);
  assert.deepEqual(rows, [
    { configId: "cfg1", configName: "基础版", allocated: 10, used: 2, creatorRemaining: 7 },
    { configId: "cfg3", configName: "标准版", allocated: 0, used: 0, creatorRemaining: 18 },
  ]);
  assert.equal(maxAdd(rows[0]), 7);
  assert.equal(maxRevoke(rows[0]), 8);
  assert.equal(maxRevoke(rows[1]), 0);
  assert.equal(maxRevoke({ allocated: 3, used: 5 }), 0);
  assert.deepEqual(buildDeltas(rows, "add", { cfg1: 3, cfg3: 10 }), { items: [{ configId: "cfg1", delta: 3 }, { configId: "cfg3", delta: 10 }], error: "" });
  assert.equal(buildDeltas(rows, "add", { cfg1: 8 }).error, "「基础版」你只剩 7 次");
  assert.deepEqual(buildDeltas(rows, "revoke", { cfg1: 8 }).items, [{ configId: "cfg1", delta: -8 }]);
  assert.equal(buildDeltas(rows, "revoke", { cfg1: 9 }).error, "「基础版」最多只能收回 8 次（已用的不能收回）");
  assert.equal(buildDeltas(rows, "revoke", {}).error, "请填写要收回的次数");
  // 创建人不限次数
  assert.equal(maxAdd(adjustRows(quota, true)[0]), null);
});

test("停用退回次数", () => {
  assert.equal(refundableTotal({ refundableTotal: 5, items: [] }), 5);
  assert.equal(refundableTotal({ items: [{ allocated: 10, used: 4 }, { allocated: 2, used: 3 }] }), 6);
  assert.equal(refundableTotal(null), 0);
});
