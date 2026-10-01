/**
 * 注册码次数、子用户额度的计算（纯函数，node --test 直接测；类型见 regcode-quota.d.ts）。
 * 规则见 api-paths-final.md 第十二节和 regcode-subusers-spec.md：
 * - 次数按配置分别计算；分给子用户从自己的剩余里扣
 * - 收回只能收回子用户「未用」的（分配数不能低于已用）
 * - createdCount 只算启用中的子用户
 */

const num = (v) => (Number.isFinite(Number(v)) ? Number(v) : 0);

/* ---------------- 标签页显隐 ---------------- */

/**
 * 是否要先查一次子用户列表才能决定显不显示「子用户」页：
 * 上限 0 且启用中的为 0 时，可能还有停用的（停用的不算已建数），要看列表。
 */
export function needsSubUserListCheck(regCode) {
  return !!regCode && !regCode.isSubUser && !!regCode.canManageSubUsers && num(regCode.maxSubUsers) === 0 && num(regCode.createdCount) === 0;
}

/**
 * 显示「生成 / 子用户」两个标签页吗。
 * 不显示：子用户登录；不能管理子用户；上限 0 且一个都没建过（listCount 为列表条数，未查时传 null）。
 */
export function showSubUserTab(regCode, listCount = null) {
  if (!regCode || regCode.isSubUser || !regCode.canManageSubUsers) return false;
  if (needsSubUserListCheck(regCode)) return num(listCount) > 0;
  return true;
}

/* ---------------- 已建数 / 上限 ---------------- */

/** 顶行「已建 N / 最多 M 个」：超过上限（管理员把上限调小）用提醒色；建满后不能新建 */
export function limitState(createdCount, maxSubUsers) {
  const n = num(createdCount);
  const m = num(maxSubUsers);
  return {
    text: `已建 ${n} / 最多 ${m} 个`,
    over: n > m,
    full: n >= m,
    canCreate: n < m,
  };
}

/* ---------------- 次数合计 ---------------- */

/** 各配置合计：已用、分配、剩余 */
export function sumQuota(items) {
  const list = Array.isArray(items) ? items : [];
  const used = list.reduce((s, i) => s + num(i.used), 0);
  const allocated = list.reduce((s, i) => s + num(i.allocated), 0);
  return { used, allocated, remaining: Math.max(0, allocated - used) };
}

/** 「已用 / 分配」是否用完（用完显示提醒色；分配 0 也算用完，提示去分配） */
export function isExhausted(usedTotal, allocatedTotal) {
  return num(usedTotal) >= num(allocatedTotal);
}

/** 列表文案「12 / 50」 */
export function usageText(usedTotal, allocatedTotal) {
  return `${num(usedTotal)} / ${num(allocatedTotal)}`;
}

/** 列表排序：启用的在前（保持原顺序），停用的排最后 */
export function sortSubUsers(items) {
  const list = Array.isArray(items) ? items : [];
  return [...list.filter((u) => Number(u.status) === 1), ...list.filter((u) => Number(u.status) !== 1)];
}

/* ---------------- 生成页 ---------------- */

/** 生成页当前配置剩余；不限次数返回 null；items 为空的旧返回用合计 */
export function remainingForConfig(quota, configId) {
  if (!quota) return null;
  if (quota.unlimited) return null;
  const items = Array.isArray(quota.items) ? quota.items : [];
  if (!items.length) return num(quota.remaining);
  const hit = items.find((i) => String(i.configId) === String(configId));
  return hit ? Math.max(0, num(hit.remaining ?? num(hit.allocated) - num(hit.used))) : 0;
}

/* ---------------- 新建子用户 ---------------- */

/** 新建表单的「分配次数」行：每个配置名 + 你还剩 N 次（不限次数时 remaining = null） */
export function createQuotaRows(myQuota, configs = []) {
  if (myQuota && myQuota.unlimited) {
    return (Array.isArray(configs) ? configs : []).map((c) => ({
      configId: String(c.id),
      configName: c.name || c.componentName || String(c.id),
      remaining: null,
    }));
  }
  const items = myQuota && Array.isArray(myQuota.items) ? myQuota.items : [];
  return sortByConfigOrder(
    items.map((i) => ({
      configId: String(i.configId),
      configName: i.configName || String(i.configId),
      remaining: Math.max(0, num(i.remaining ?? num(i.allocated) - num(i.used))),
    })),
    configs,
  );
}

/**
 * 配置行按固定顺序排（新建、额度、重新启用后的额度三个窗口一致）：
 * 按配置列表（GET /common/regCodeConfig/list，生成页下拉的顺序）里的位置；列表里没有的排后面，保持原来的先后。
 */
export function sortByConfigOrder(rows, configs = []) {
  const order = new Map((Array.isArray(configs) ? configs : []).map((c, i) => [String(c.id), i]));
  return rows
    .map((r, i) => ({ r, i, o: order.has(String(r.configId)) ? order.get(String(r.configId)) : Infinity }))
    .sort((a, b) => (a.o === b.o ? a.i - b.i : a.o - b.o))
    .map((x) => x.r);
}

/** 新建请求里的 quotas：只带大于 0 的；超过剩余返回错误 */
export function buildCreateQuotas(rows, values) {
  const quotas = [];
  for (const r of rows) {
    const v = Math.floor(num(values[r.configId]));
    if (v < 0) return { quotas: [], error: `「${r.configName}」次数不能小于 0` };
    if (!v) continue;
    if (r.remaining != null && v > r.remaining) return { quotas: [], error: `「${r.configName}」你只剩 ${r.remaining} 次` };
    quotas.push({ configId: r.configId, count: v });
  }
  return { quotas, error: "" };
}

/** 新建表单校验：账号 3–20 位，初始密码至少 6 位 */
export function validateSubUserForm(form) {
  const username = String(form.username || "").trim();
  if (username.length < 3 || username.length > 20) return "账号需要 3–20 位";
  if (!/^[A-Za-z0-9_.@-]+$/.test(username)) return "账号只能用字母、数字和 _ . @ -";
  if (String(form.password || "").length < 6) return "初始密码至少 6 位";
  return "";
}

/* ---------------- 额度调整 ---------------- */

/**
 * 额度窗口的行：子用户各配置（已用、分配）+ 创建人各配置剩余，按配置合并。
 * 创建人有、子用户还没有的配置也列出来（分配 0），方便追加。行按配置列表的顺序排（sortByConfigOrder）。
 */
export function adjustRows(quota, creatorUnlimited = false, configs = []) {
  const items = quota && Array.isArray(quota.items) ? quota.items : [];
  const creator = quota && Array.isArray(quota.creatorRemaining) ? quota.creatorRemaining : [];
  const map = new Map();
  for (const i of items) {
    map.set(String(i.configId), {
      configId: String(i.configId),
      configName: i.configName || String(i.configId),
      allocated: num(i.allocated),
      used: num(i.used),
      creatorRemaining: creatorUnlimited ? null : 0,
    });
  }
  for (const c of creator) {
    const key = String(c.configId);
    const remaining = Math.max(0, num(c.remaining ?? num(c.allocated) - num(c.used)));
    const row = map.get(key);
    if (row) row.creatorRemaining = creatorUnlimited ? null : remaining;
    else map.set(key, { configId: key, configName: c.configName || key, allocated: 0, used: 0, creatorRemaining: creatorUnlimited ? null : remaining });
  }
  return sortByConfigOrder([...map.values()], configs);
}

/** 追加上限：创建人该配置的剩余（不限次数为 null） */
export function maxAdd(row) {
  return row.creatorRemaining == null ? null : Math.max(0, num(row.creatorRemaining));
}

/** 额度窗口里的「当前额度 N」：分配 − 已用（重新启用后后端返回已用 3、分配 3，显示当前额度 0） */
export function currentQuota(row) {
  return Math.max(0, num(row && row.allocated) - num(row && row.used));
}

/** 收回上限：子用户未用的次数（分配不能低于已用） */
export function maxRevoke(row) {
  return Math.max(0, num(row.allocated) - num(row.used));
}

/** 额度调整请求：mode = add | revoke；只带大于 0 的行；超上限返回错误 */
export function buildDeltas(rows, mode, values) {
  const items = [];
  for (const r of rows) {
    const v = Math.floor(num(values[r.configId]));
    if (v < 0) return { items: [], error: "次数不能小于 0" };
    if (!v) continue;
    if (mode === "add") {
      const max = maxAdd(r);
      if (max != null && v > max) return { items: [], error: `「${r.configName}」你只剩 ${max} 次` };
      items.push({ configId: r.configId, delta: v });
    } else {
      const max = maxRevoke(r);
      if (v > max) return { items: [], error: `「${r.configName}」最多只能收回 ${max} 次（已用的不能收回）` };
      items.push({ configId: r.configId, delta: -v });
    }
  }
  if (!items.length) return { items, error: mode === "add" ? "请填写要追加的次数" : "请填写要收回的次数" };
  return { items, error: "" };
}

/** 停用会退回多少次：优先用接口给的 refundableTotal，没有就按各配置未用算 */
export function refundableTotal(quota) {
  if (!quota) return 0;
  if (quota.refundableTotal != null) return num(quota.refundableTotal);
  return (Array.isArray(quota.items) ? quota.items : []).reduce((s, i) => s + Math.max(0, num(i.allocated) - num(i.used)), 0);
}
