import test from "node:test";
import assert from "node:assert/strict";
import { canAccessMenu, homeToolMenus, menusFromLegacyFlags, normalizeAppMenus } from "./app-menus.js";

const manifest = [
  { path: "/crab", parent: null, hidden: false },
  { path: "/crab/new", parent: "/crab", hidden: true },
  { path: "/crab/:id", parent: "/crab", hidden: true },
  { path: "/regcode", parent: null, hidden: false },
];

test("设计文档格式原样保留并按 sort 排序", () => {
  const list = normalizeAppMenus([
    { id: "b", path: "/regcode", name: "注册码生成", parent: null, type: "page", icon: "key", sort: 2, hidden: false, component: "regcode/index" },
    { id: "a", path: "/crab", name: "螃蟹出货", parent: null, type: "page", icon: "crab", sort: 1, hidden: false, component: "crab/list" },
  ]);
  assert.deepEqual(
    list.map((m) => m.path),
    ["/crab", "/regcode"],
  );
  assert.equal(list[0].name, "螃蟹出货");
  assert.equal(list[0].parent, null);
});

test("后台菜单字段格式：menuUrl 相对父级时拼完整路径", () => {
  const list = normalizeAppMenus([
    { id: "1", menuName: "螃蟹出货", menuUrl: "/crab", parentId: null, sortOrder: 1, icon: "crab", visible: 1, menuType: "C" },
    { id: "2", menuName: "新建", menuUrl: "new", parentId: "1", sortOrder: 2, visible: 0, menuType: "C" },
    { id: "3", menuName: "编辑", routeKey: "/crab/:id", menuUrl: ":id", parentId: "1", sortOrder: 3, visible: "0", menuType: "C" },
    { id: "4", menuName: "工具", menuUrl: "/tools", parentId: null, sortOrder: 0, visible: 1, menuType: "M" },
  ]);
  const byPath = Object.fromEntries(list.map((m) => [m.path, m]));
  assert.ok(byPath["/crab"]);
  assert.equal(byPath["/crab/new"].parent, "/crab");
  assert.equal(byPath["/crab/new"].hidden, true);
  assert.equal(byPath["/crab/:id"].hidden, true);
  assert.equal(byPath["/tools"].type, "dir");
  assert.equal(byPath["/crab"].hidden, false);
});

test("非数组或空值返回空数组", () => {
  assert.deepEqual(normalizeAppMenus(null), []);
  assert.deepEqual(normalizeAppMenus([null, 1, "x"]), []);
});

test("授权判断：hidden 子页跟随父级", () => {
  const menus = normalizeAppMenus([{ id: "a", path: "/crab", name: "螃蟹出货", type: "page", sort: 1 }]);
  assert.equal(canAccessMenu(menus, "/crab", manifest), true);
  assert.equal(canAccessMenu(menus, "/crab/:id", manifest), true);
  assert.equal(canAccessMenu(menus, "/regcode", manifest), false);
  assert.equal(canAccessMenu([], "/crab", manifest), false);
});

test("首页工具卡只要非 hidden 的页面", () => {
  const menus = normalizeAppMenus([
    { id: "a", path: "/crab", name: "螃蟹出货", type: "page", sort: 2 },
    { id: "b", path: "/crab/new", name: "新建", parent: "/crab", type: "page", sort: 3, hidden: true },
    { id: "c", path: "/tools", name: "工具", type: "dir", sort: 0 },
    { id: "d", path: "/regcode", name: "注册码生成", type: "page", sort: 1 },
  ]);
  assert.deepEqual(
    homeToolMenus(menus).map((m) => m.path),
    ["/regcode", "/crab"],
  );
});

test("过渡兼容：appMenus 为空时按后端 canUseCrab / canUseRegCode 拼菜单", () => {
  const full = [
    { path: "/crab", name: "螃蟹出货", parent: null, type: "page", icon: "crab", sort: 1, hidden: false, component: "crab/list" },
    { path: "/crab/new", name: "录入", parent: "/crab", type: "page", sort: 2, hidden: true, component: "crab/entry" },
    { path: "/crab/:id", name: "详情", parent: "/crab", type: "page", sort: 3, hidden: true, component: "crab/edit" },
    { path: "/regcode", name: "注册码生成", parent: null, type: "page", icon: "key", sort: 4, hidden: false, component: "regcode/index" },
  ];
  assert.deepEqual(
    menusFromLegacyFlags({ canUseCrab: true, canUseRegCode: false }, full).map((m) => m.path),
    ["/crab", "/crab/new", "/crab/:id"],
  );
  assert.deepEqual(
    menusFromLegacyFlags({ canUseCrab: false, canUseRegCode: true }, full).map((m) => m.path),
    ["/regcode"],
  );
  assert.deepEqual(menusFromLegacyFlags({}, full), []);
  assert.deepEqual(menusFromLegacyFlags(null, full), []);
});
