import { flattenBookmarks, getBookmarkTree, listFolders } from "../../shared/bookmarks.js";
import { getPreferences, savePreferences } from "../../shared/storage.js";
import { calculateBookmarkUpdates, isHttpPrefix, normalizePrefix } from "../../shared/url-rules.js";
import { debounce, makeId } from "../../shared/ui.js";

const elements = {
  navLinks: [...document.querySelectorAll("[data-view]")],
  panels: [...document.querySelectorAll("[data-view-panel]")],
  generalForm: document.querySelector("#generalForm"),
  sitesForm: document.querySelector("#sitesForm"),
  rulesForm: document.querySelector("#rulesForm"),
  openOnStartup: document.querySelector("#openOnStartup"),
  showFrequent: document.querySelector("#showFrequent"),
  showCustomSites: document.querySelector("#showCustomSites"),
  showBookmarks: document.querySelector("#showBookmarks"),
  showHiddenBookmarks: document.querySelector("#showHiddenBookmarks"),
  bookmarkRootId: document.querySelector("#bookmarkRootId"),
  frequentLimit: document.querySelector("#frequentLimit"),
  searchEngine: document.querySelector("#searchEngine"),
  siteRows: document.querySelector("#siteRows"),
  ruleRows: document.querySelector("#ruleRows"),
  siteRowTemplate: document.querySelector("#siteRowTemplate"),
  ruleRowTemplate: document.querySelector("#ruleRowTemplate"),
  addSite: document.querySelector("#addSite"),
  addRule: document.querySelector("#addRule"),
  openNewTab: document.querySelector("#openNewTab"),
  openShortcutSettings: document.querySelector("#openShortcutSettings"),
  previewUpdates: document.querySelector("#previewUpdates"),
  previewDialog: document.querySelector("#previewDialog"),
  previewSummary: document.querySelector("#previewSummary"),
  previewList: document.querySelector("#previewList"),
  applyUpdates: document.querySelector("#applyUpdates"),
  cancelPreview: document.querySelector("#cancelPreview"),
  closePreview: document.querySelector("#closePreview"),
  mappingStatus: document.querySelector("#mappingStatus"),
  toast: document.querySelector("#toast")
};

let preferences;
let bookmarkTree;
let folders = [];
let pendingUpdates = [];

function showToast(message, type = "success") {
  elements.toast.textContent = message;
  elements.toast.dataset.type = type;
  elements.toast.classList.remove("hidden");
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => elements.toast.classList.add("hidden"), 2800);
}

function showView() {
  const requested = location.hash.slice(1);
  const view = ["general", "sites", "mappings"].includes(requested) ? requested : "general";
  for (const link of elements.navLinks) link.classList.toggle("is-active", link.dataset.view === view);
  for (const panel of elements.panels) panel.classList.toggle("hidden", panel.dataset.viewPanel !== view);
  document.title = `${view === "general" ? "常规设置" : view === "sites" ? "自定义网站" : "地址映射"} - EDGE工作台`;
}

function fillFolderSelect(select, selectedId = "", includeAll = false) {
  select.replaceChildren();
  if (includeAll) {
    const all = document.createElement("option");
    all.value = "";
    all.textContent = "所有收藏夹";
    select.append(all);
  }
  for (const folder of folders) {
    const option = document.createElement("option");
    option.value = folder.id;
    option.textContent = `${"　".repeat(Math.min(folder.depth, 4))}${folder.title}`;
    option.selected = folder.id === selectedId;
    select.append(option);
  }
}

function renderGeneral() {
  elements.openOnStartup.checked = preferences.openOnStartup;
  elements.showFrequent.checked = preferences.showFrequent;
  elements.showCustomSites.checked = preferences.showCustomSites;
  elements.showBookmarks.checked = preferences.showBookmarks;
  elements.showHiddenBookmarks.checked = preferences.showHiddenBookmarks;
  elements.frequentLimit.value = String(preferences.frequentLimit);
  elements.searchEngine.value = preferences.searchEngine;
  fillFolderSelect(elements.bookmarkRootId, preferences.bookmarkRootId);
}

function addSiteRow(site = {}) {
  const row = elements.siteRowTemplate.content.firstElementChild.cloneNode(true);
  row.dataset.id = site.id || makeId();
  row.querySelector('[data-field="name"]').value = site.name || "";
  row.querySelector('[data-field="url"]').value = site.url || "";
  row.querySelector(".remove-row").addEventListener("click", () => {
    row.remove();
    updateSiteIndexes();
    if (!elements.siteRows.children.length) renderSitesEmpty();
  });
  elements.siteRows.append(row);
  updateSiteIndexes();
  row.querySelector('[data-field="name"]').focus();
}

function renderSitesEmpty() {
  if (elements.siteRows.children.length) return;
  const empty = document.createElement("div");
  empty.className = "empty-state sites-empty";
  empty.textContent = "还没有自定义网站";
  elements.siteRows.append(empty);
}

function updateSiteIndexes() {
  [...elements.siteRows.querySelectorAll(".site-editor-row")].forEach((row, index) => {
    row.querySelector(".drag-index").textContent = String(index + 1).padStart(2, "0");
  });
}

function renderSites() {
  elements.siteRows.replaceChildren();
  for (const site of preferences.customSites) addSiteRow(site);
  renderSitesEmpty();
}

function addRuleRow(rule = {}) {
  const row = elements.ruleRowTemplate.content.firstElementChild.cloneNode(true);
  row.dataset.id = rule.id || makeId();
  row.querySelector('[data-field="enabled"]').checked = rule.enabled !== false;
  row.querySelector('[data-field="name"]').value = rule.name || "";
  row.querySelector('[data-field="sourcePrefixes"]').value = Array.isArray(rule.sourcePrefixes)
    ? rule.sourcePrefixes.join("\n")
    : String(rule.sourcePrefixes || "").split(/[\r\n,;]+/).filter(Boolean).join("\n");
  row.querySelector('[data-field="targetPrefix"]').value = rule.targetPrefix || "";
  fillFolderSelect(row.querySelector('[data-field="folderId"]'), rule.folderId || "", true);
  row.querySelector(".remove-row").addEventListener("click", () => {
    row.remove();
    if (!elements.ruleRows.children.length) renderRulesEmpty();
  });
  elements.ruleRows.append(row);
  row.querySelector('[data-field="name"]').focus();
}

function renderRulesEmpty() {
  if (elements.ruleRows.children.length) return;
  const empty = document.createElement("div");
  empty.className = "empty-state rules-empty";
  empty.textContent = "还没有地址映射规则";
  elements.ruleRows.append(empty);
}

function renderRules() {
  elements.ruleRows.replaceChildren();
  for (const rule of preferences.prefixRules) addRuleRow(rule);
  renderRulesEmpty();
  renderMappingStatus();
}

function renderMappingStatus() {
  if (!bookmarkTree || !elements.mappingStatus) return;
  try {
    const rules = collectRules();
    const updates = calculateBookmarkUpdates(flattenBookmarks(bookmarkTree, []), rules);
    elements.mappingStatus.textContent = rules.length
      ? `当前规则可匹配 ${updates.length} 条收藏链接${updates.length ? "，点击工作台卡片时会自动切换地址" : "；请检查旧地址前缀和绑定文件夹"}`
      : "尚未配置地址映射规则";
    elements.mappingStatus.dataset.type = updates.length ? "success" : "neutral";
  } catch {
    elements.mappingStatus.textContent = "规则尚未填写完整，保存后会显示匹配数量";
    elements.mappingStatus.dataset.type = "neutral";
  }
}

function collectSites() {
  return [...elements.siteRows.querySelectorAll(".site-editor-row")].map((row) => ({
    id: row.dataset.id,
    name: row.querySelector('[data-field="name"]').value.trim(),
    url: row.querySelector('[data-field="url"]').value.trim()
  })).filter((site) => site.name || site.url);
}

function validateSites(sites) {
  for (const site of sites) {
    if (!site.name) throw new Error("请为每个自定义网站填写名称");
    if (!isHttpPrefix(site.url)) throw new Error(`“${site.name}”的网址必须以 http:// 或 https:// 开头`);
  }
}

function collectRules() {
  const previousById = new Map(preferences.prefixRules.map((rule) => [rule.id, rule]));
  return [...elements.ruleRows.querySelectorAll(".rule-editor-row")].map((row) => {
    const id = row.dataset.id;
    const targetPrefix = normalizePrefix(row.querySelector('[data-field="targetPrefix"]').value);
    const sourcePrefixes = row.querySelector('[data-field="sourcePrefixes"]').value
      .split(/[\r\n,;]+/)
      .map(normalizePrefix)
      .filter(Boolean);
    const previousTarget = normalizePrefix(previousById.get(id)?.targetPrefix);
    if (previousTarget && previousTarget !== targetPrefix && !sourcePrefixes.includes(previousTarget)) {
      sourcePrefixes.push(previousTarget);
    }
    return {
      id,
      enabled: row.querySelector('[data-field="enabled"]').checked,
      name: row.querySelector('[data-field="name"]').value.trim(),
      folderId: row.querySelector('[data-field="folderId"]').value,
      sourcePrefixes: [...new Set(sourcePrefixes)],
      targetPrefix
    };
  }).filter((rule) => rule.name || rule.sourcePrefixes.length || rule.targetPrefix);
}

function validateRules(rules) {
  for (const rule of rules) {
    if (!rule.name) throw new Error("请为每条地址映射填写规则名称");
    if (!isHttpPrefix(rule.targetPrefix)) throw new Error(`“${rule.name}”的当前地址必须是有效的 HTTP(S) 前缀`);
    if (!rule.sourcePrefixes.length) throw new Error(`“${rule.name}”至少需要一个旧地址前缀`);
    if (rule.sourcePrefixes.some((source) => !isHttpPrefix(source))) {
      throw new Error(`“${rule.name}”包含无效的旧地址前缀`);
    }
  }
}

async function saveRulesFromForm() {
  const rules = collectRules();
  validateRules(rules);
  preferences = await savePreferences({ ...preferences, prefixRules: rules });
  renderRules();
  return rules;
}

function renderPreview(updates) {
  elements.previewList.replaceChildren();
  elements.previewSummary.textContent = updates.length
    ? `将永久修改 ${updates.length} 个收藏链接。此操作会写入 Edge 收藏夹。`
    : "没有收藏链接需要更新。请检查规则的旧地址和绑定文件夹。";
  elements.applyUpdates.disabled = updates.length === 0;
  for (const update of updates) {
    const item = document.createElement("div");
    item.className = "preview-item";
    const title = document.createElement("strong");
    title.textContent = update.title;
    const oldUrl = document.createElement("span");
    oldUrl.className = "preview-url";
    oldUrl.textContent = update.oldUrl;
    const newUrl = document.createElement("span");
    newUrl.className = "preview-url preview-url--new";
    newUrl.textContent = `→ ${update.newUrl}`;
    item.append(title, oldUrl, newUrl);
    elements.previewList.append(item);
  }
  elements.previewDialog.showModal();
}

async function preparePreview() {
  const rules = await saveRulesFromForm();
  bookmarkTree = await getBookmarkTree();
  const allBookmarks = flattenBookmarks(bookmarkTree, []);
  pendingUpdates = calculateBookmarkUpdates(allBookmarks, rules);
  renderPreview(pendingUpdates);
}

elements.generalForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  preferences = await savePreferences({
    ...preferences,
    openOnStartup: elements.openOnStartup.checked,
    showFrequent: elements.showFrequent.checked,
    showCustomSites: elements.showCustomSites.checked,
    showBookmarks: elements.showBookmarks.checked,
    showHiddenBookmarks: elements.showHiddenBookmarks.checked,
    bookmarkRootId: elements.bookmarkRootId.value,
    frequentLimit: Math.max(4, Math.min(24, Number(elements.frequentLimit.value) || 8)),
    searchEngine: elements.searchEngine.value
  });
  renderGeneral();
  showToast("常规设置已保存");
});

elements.sitesForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const customSites = collectSites();
    validateSites(customSites);
    preferences = await savePreferences({ ...preferences, customSites });
    renderSites();
    showToast("自定义网站已保存");
  } catch (error) {
    showToast(error.message, "error");
  }
});

elements.rulesForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    await saveRulesFromForm();
    showToast("地址映射规则已保存");
  } catch (error) {
    showToast(error.message, "error");
  }
});

elements.addSite.addEventListener("click", () => {
  elements.siteRows.querySelector(".sites-empty")?.remove();
  addSiteRow();
});
elements.addRule.addEventListener("click", () => {
  elements.ruleRows.querySelector(".rules-empty")?.remove();
  addRuleRow();
});
elements.openNewTab.addEventListener("click", () => chrome.tabs.create({ url: chrome.runtime.getURL("pages/newtab/index.html") }));
elements.openShortcutSettings.addEventListener("click", () => chrome.tabs.create({ url: "edge://extensions/shortcuts" }));
elements.previewUpdates.addEventListener("click", () => preparePreview().catch((error) => showToast(error.message, "error")));
elements.rulesForm.addEventListener("input", debounce(renderMappingStatus, 220));
elements.cancelPreview.addEventListener("click", () => elements.previewDialog.close());
elements.closePreview.addEventListener("click", () => elements.previewDialog.close());
elements.applyUpdates.addEventListener("click", async () => {
  elements.applyUpdates.disabled = true;
  try {
    const results = await Promise.allSettled(pendingUpdates.map((update) => chrome.bookmarks.update(update.id, { url: update.newUrl })));
    const failed = results.filter((result) => result.status === "rejected").length;
    elements.previewDialog.close();
    pendingUpdates = [];
    showToast(failed ? `已更新 ${results.length - failed} 个，${failed} 个失败` : `已更新 ${results.length} 个收藏链接`, failed ? "error" : "success");
  } finally {
    elements.applyUpdates.disabled = false;
  }
});
window.addEventListener("hashchange", showView);

async function initialize() {
  [preferences, bookmarkTree] = await Promise.all([getPreferences(), getBookmarkTree()]);
  folders = listFolders(bookmarkTree);
  renderGeneral();
  renderSites();
  renderRules();
  showView();
}

initialize().catch((error) => showToast(error.message || "设置加载失败", "error"));
