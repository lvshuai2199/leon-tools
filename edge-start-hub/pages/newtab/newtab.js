import { findMainBookmarkFolder, flattenBookmarks, getBookmarkTree } from "../../shared/bookmarks.js";
import { getFrequentPages, getSearchUrl, looksLikeUrl, toNavigableUrl } from "../../shared/browser-data.js";
import { getPreferences, savePreferences, watchPreferences } from "../../shared/storage.js";
import { resolveMappedUrl } from "../../shared/url-rules.js";
import { createFavicon, debounce, getHostLabel } from "../../shared/ui.js";

const elements = {
  searchForm: document.querySelector("#searchForm"),
  searchInput: document.querySelector("#searchInput"),
  searchResults: document.querySelector("#searchResults"),
  frequentSection: document.querySelector("#frequentSection"),
  frequentGrid: document.querySelector("#frequentGrid"),
  customSection: document.querySelector("#customSection"),
  customGrid: document.querySelector("#customGrid"),
  bookmarksSection: document.querySelector("#bookmarksSection"),
  bookmarkGroups: document.querySelector("#bookmarkGroups"),
  bookmarkCount: document.querySelector("#bookmarkCount"),
  manageSites: document.querySelector("#manageSites"),
  toast: document.querySelector("#toast"),
  contextMenu: document.querySelector("#bookmarkContextMenu"),
  bookmarkEditDialog: document.querySelector("#bookmarkEditDialog"),
  bookmarkEditForm: document.querySelector("#bookmarkEditForm"),
  bookmarkEditTitle: document.querySelector("#bookmarkEditTitle"),
  bookmarkEditUrl: document.querySelector("#bookmarkEditUrl"),
  cancelBookmarkEdit: document.querySelector("#cancelBookmarkEdit"),
  closeBookmarkEdit: document.querySelector("#closeBookmarkEdit"),
  qrDialog: document.querySelector("#qrDialog"),
  qrLoading: document.querySelector("#qrLoading"),
  qrImage: document.querySelector("#qrImage"),
  qrUrl: document.querySelector("#qrUrl"),
  closeQr: document.querySelector("#closeQr")
};

let preferences;
let bookmarks = [];
let frequentPages = [];
let activeResults = [];
let selectedResultIndex = -1;
let contextBookmark = null;
let editingBookmark = null;
let draggedBookmark = null;
let draggedElement = null;
let dropTarget = null;

function isPinned(bookmark) {
  return preferences.pinnedBookmarkIds.includes(bookmark.id);
}

function isHidden(bookmark) {
  return preferences.hiddenBookmarkIds.includes(bookmark.id);
}

function getBookmarkUrl(bookmark) {
  return resolveMappedUrl(bookmark.url, preferences.prefixRules, bookmark.ancestorIds);
}

function makeInlineIcon(symbol, title, className = "") {
  const icon = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  icon.classList.add("icon", className);
  icon.setAttribute("title", title);
  icon.setAttribute("aria-label", title);
  const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
  use.setAttribute("href", `../../assets/icons.svg#${symbol}`);
  icon.append(use);
  return icon;
}

function showToast(message, type = "success") {
  elements.toast.textContent = message;
  elements.toast.dataset.type = type;
  elements.toast.classList.remove("hidden");
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => elements.toast.classList.add("hidden"), 2600);
}

function createSiteTile(site) {
  const link = document.createElement("a");
  link.className = "site-tile";
  link.href = site.url;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  link.append(createFavicon(site.url, 28));

  const copy = document.createElement("span");
  copy.className = "site-copy";
  const name = document.createElement("span");
  name.className = "site-name";
  name.textContent = site.title || site.name || getHostLabel(site.url);
  const host = document.createElement("span");
  host.className = "site-host";
  host.textContent = getHostLabel(site.url);
  copy.append(name, host);
  link.append(copy);
  return link;
}

function renderFrequent() {
  elements.frequentSection.classList.toggle("hidden", !preferences.showFrequent);
  elements.frequentGrid.replaceChildren();
  for (const page of frequentPages) elements.frequentGrid.append(createSiteTile(page));
  if (frequentPages.length === 0) {
    const empty = document.createElement("div");
    empty.className = "empty-state";
    empty.textContent = "浏览一段时间后，常用网页会显示在这里";
    elements.frequentGrid.append(empty);
  }
}

function renderCustomSites() {
  elements.customSection.classList.toggle("hidden", !preferences.showCustomSites);
  elements.customGrid.replaceChildren();
  for (const site of preferences.customSites) elements.customGrid.append(createSiteTile(site));
  if (preferences.customSites.length === 0) {
    const empty = document.createElement("div");
    empty.className = "empty-state";
    empty.textContent = "在设置中添加常用网站";
    elements.customGrid.append(empty);
  }
}

function createBookmarkLink(bookmark, index) {
  const mappedUrl = getBookmarkUrl(bookmark);
  const link = document.createElement("a");
  link.className = `bookmark-link${isPinned(bookmark) ? " is-pinned" : ""}${isHidden(bookmark) ? " is-hidden-bookmark" : ""}`;
  link.style.setProperty("--tile-index", String(index));
  link.href = mappedUrl;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  link.draggable = true;
  link.dataset.bookmarkId = bookmark.id;
  link.dataset.parentId = bookmark.parentId;
  link.title = mappedUrl === bookmark.url ? bookmark.url : `${bookmark.url}\n→ ${mappedUrl}`;
  link.append(createFavicon(mappedUrl, 20));

  const copy = document.createElement("span");
  copy.className = "bookmark-copy";
  const title = document.createElement("span");
  title.className = "bookmark-title";
  title.textContent = bookmark.title;
  const folder = document.createElement("small");
  folder.className = "bookmark-folder";
  folder.textContent = bookmark.path.at(-1) || "主收藏夹";
  copy.append(title, folder);
  link.append(copy);
  const flags = document.createElement("span");
  flags.className = "bookmark-flags";
  if (isPinned(bookmark)) flags.append(makeInlineIcon("pin", "已置顶", "bookmark-pin"));
  if (mappedUrl !== bookmark.url) flags.append(makeInlineIcon("route", "已切换服务器地址", "mapped-indicator"));
  link.append(flags);
  link.addEventListener("contextmenu", (event) => showBookmarkContextMenu(event, bookmark));
  link.addEventListener("dragstart", (event) => startBookmarkDrag(event, bookmark, link));
  link.addEventListener("dragover", (event) => dragOverBookmark(event, bookmark, link));
  link.addEventListener("drop", (event) => dropOnBookmark(event, bookmark, link));
  link.addEventListener("dragend", finishBookmarkDrag);
  return link;
}

function clearDropTarget() {
  if (!dropTarget) return;
  dropTarget.element.classList.remove("drop-before", "drop-after");
  dropTarget = null;
}

function startBookmarkDrag(event, bookmark, link) {
  hideBookmarkContextMenu();
  draggedBookmark = bookmark;
  draggedElement = link;
  link.classList.add("is-dragging");
  event.dataTransfer.effectAllowed = "move";
  event.dataTransfer.setData("text/plain", bookmark.id);
}

function dragOverBookmark(event, targetBookmark, targetElement) {
  if (!draggedBookmark || draggedBookmark.id === targetBookmark.id) return;
  event.preventDefault();
  event.dataTransfer.dropEffect = "move";
  const bounds = targetElement.getBoundingClientRect();
  const position = event.clientX < bounds.left + bounds.width / 2 ? "before" : "after";
  if (dropTarget?.element !== targetElement || dropTarget.position !== position) {
    clearDropTarget();
    targetElement.classList.add(position === "before" ? "drop-before" : "drop-after");
    dropTarget = { element: targetElement, bookmark: targetBookmark, position };
  }
}

async function dropOnBookmark(event, targetBookmark, targetElement) {
  event.preventDefault();
  if (!draggedBookmark || draggedBookmark.id === targetBookmark.id) return;
  const bounds = targetElement.getBoundingClientRect();
  const position = event.clientX < bounds.left + bounds.width / 2 ? "before" : "after";
  await moveBookmarkTo(draggedBookmark, targetBookmark.parentId, targetBookmark.id, position);
}

async function moveBookmarkTo(bookmark, parentId, targetBookmarkId = "", position = "after") {
  try {
    const siblings = (await chrome.bookmarks.getChildren(parentId)).filter((item) => item.id !== bookmark.id);
    const targetIndex = targetBookmarkId ? siblings.findIndex((item) => item.id === targetBookmarkId) : siblings.length;
    const index = targetBookmarkId
      ? Math.max(0, targetIndex + (position === "after" ? 1 : 0))
      : siblings.length;
    await chrome.bookmarks.move(bookmark.id, { parentId, index });
    showToast(bookmark.parentId === parentId ? "书签顺序已调整" : "书签已移动到新分组");
    await refresh();
  } catch (error) {
    showToast(error.message || "移动书签失败", "error");
  } finally {
    finishBookmarkDrag();
  }
}

function finishBookmarkDrag() {
  draggedElement?.classList.remove("is-dragging");
  clearDropTarget();
  document.querySelectorAll(".bookmark-group.is-drop-target").forEach((group) => group.classList.remove("is-drop-target"));
  draggedBookmark = null;
  draggedElement = null;
}

function hideBookmarkContextMenu() {
  elements.contextMenu.classList.add("hidden");
  contextBookmark = null;
}

function updateContextMenuLabels() {
  if (!contextBookmark) return;
  const pinItem = elements.contextMenu.querySelector('[data-action="pin"] span');
  const hideItem = elements.contextMenu.querySelector('[data-action="hide"] span');
  pinItem.textContent = isPinned(contextBookmark) ? "取消置顶" : "置顶";
  hideItem.textContent = isHidden(contextBookmark) ? "取消隐藏" : "隐藏";
}

function showBookmarkContextMenu(event, bookmark) {
  event.preventDefault();
  event.stopPropagation();
  contextBookmark = bookmark;
  updateContextMenuLabels();
  elements.contextMenu.classList.remove("hidden");
  const menuRect = elements.contextMenu.getBoundingClientRect();
  const left = Math.min(event.clientX, window.innerWidth - menuRect.width - 10);
  const top = Math.min(event.clientY, window.innerHeight - menuRect.height - 10);
  elements.contextMenu.style.left = `${Math.max(10, left)}px`;
  elements.contextMenu.style.top = `${Math.max(10, top)}px`;
  elements.contextMenu.querySelector('[role="menuitem"]')?.focus();
}

async function openBookmarkIn(action) {
  if (!contextBookmark) return;
  const url = getBookmarkUrl(contextBookmark);
  try {
    if (action === "new-tab") await chrome.tabs.create({ url });
    if (action === "new-window") await chrome.windows.create({ url });
    if (action === "incognito") await chrome.windows.create({ url, incognito: true });
    hideBookmarkContextMenu();
  } catch (error) {
    showToast(error.message || "无法打开书签", "error");
  }
}

function showQrCode(bookmark) {
  const url = getBookmarkUrl(bookmark);
  elements.qrLoading.classList.remove("hidden");
  elements.qrImage.classList.add("hidden");
  elements.qrUrl.textContent = url;
  elements.qrImage.alt = `${bookmark.title} 二维码`;
  elements.qrImage.src = `https://quickchart.io/qr?size=260&margin=2&text=${encodeURIComponent(url)}`;
  elements.qrDialog.showModal();
}

function openBookmarkEditor(bookmark) {
  editingBookmark = bookmark;
  elements.bookmarkEditTitle.value = bookmark.title;
  elements.bookmarkEditUrl.value = bookmark.url;
  elements.bookmarkEditDialog.showModal();
  elements.bookmarkEditTitle.focus();
  elements.bookmarkEditTitle.select();
}

async function toggleBookmarkPin(bookmark) {
  const ids = new Set(preferences.pinnedBookmarkIds);
  if (ids.has(bookmark.id)) ids.delete(bookmark.id);
  else ids.add(bookmark.id);
  preferences = await savePreferences({ ...preferences, pinnedBookmarkIds: [...ids] });
  hideBookmarkContextMenu();
  renderBookmarks();
  showToast(ids.has(bookmark.id) ? "书签已置顶" : "已取消置顶");
}

async function toggleBookmarkHidden(bookmark) {
  const ids = new Set(preferences.hiddenBookmarkIds);
  if (ids.has(bookmark.id)) ids.delete(bookmark.id);
  else ids.add(bookmark.id);
  preferences = await savePreferences({ ...preferences, hiddenBookmarkIds: [...ids] });
  hideBookmarkContextMenu();
  renderBookmarks();
  showToast(ids.has(bookmark.id) ? "书签已隐藏" : "已取消隐藏");
}

async function deleteBookmark(bookmark) {
  if (!confirm(`确定删除书签“${bookmark.title}”吗？\n此操作会同步删除 Edge 收藏夹中的链接。`)) return;
  try {
    await chrome.bookmarks.remove(bookmark.id);
    preferences = await savePreferences({
      ...preferences,
      pinnedBookmarkIds: preferences.pinnedBookmarkIds.filter((id) => id !== bookmark.id),
      hiddenBookmarkIds: preferences.hiddenBookmarkIds.filter((id) => id !== bookmark.id)
    });
    hideBookmarkContextMenu();
    await refresh();
    showToast("书签已删除");
  } catch (error) {
    showToast(error.message || "删除失败", "error");
  }
}

async function executeBookmarkAction(action) {
  if (!contextBookmark) return;
  const bookmark = contextBookmark;
  if (["new-tab", "new-window", "incognito"].includes(action)) return openBookmarkIn(action);
  hideBookmarkContextMenu();
  try {
    if (action === "qr") showQrCode(bookmark);
    if (action === "pin") await toggleBookmarkPin(bookmark);
    if (action === "edit") openBookmarkEditor(bookmark);
    if (action === "copy") {
      await navigator.clipboard.writeText(getBookmarkUrl(bookmark));
      showToast("网址已复制");
    }
    if (action === "hide") await toggleBookmarkHidden(bookmark);
    if (action === "delete") await deleteBookmark(bookmark);
  } catch (error) {
    showToast(error.message || "操作失败", "error");
  }
}

function renderBookmarks() {
  elements.bookmarksSection.classList.toggle("hidden", !preferences.showBookmarks);
  elements.bookmarkGroups.replaceChildren();
  elements.bookmarkCount.textContent = `${bookmarks.length} 个链接`;

  if (bookmarks.length === 0) {
    const empty = document.createElement("div");
    empty.className = "empty-state";
    empty.textContent = "主收藏夹中还没有可显示的链接";
    elements.bookmarkGroups.append(empty);
    return;
  }

  const visibleBookmarks = (preferences.showHiddenBookmarks ? [...bookmarks] : bookmarks.filter((bookmark) => !isHidden(bookmark)))
    .sort((a, b) => Number(isPinned(b)) - Number(isPinned(a)));
  elements.bookmarkCount.textContent = preferences.showHiddenBookmarks
    ? `${visibleBookmarks.length} 个链接`
    : `${visibleBookmarks.length} / ${bookmarks.length} 个链接`;
  if (visibleBookmarks.length === 0) {
    const empty = document.createElement("div");
    empty.className = "empty-state";
    empty.textContent = preferences.showHiddenBookmarks ? "主收藏夹中还没有可显示的链接" : "所有书签都已隐藏，可在设置中重新显示";
    elements.bookmarkGroups.append(empty);
    return;
  }

  const grouped = new Map();
  for (const bookmark of visibleBookmarks) {
    // Keep the folder hierarchy visible while rendering every folder expanded.
    const groupName = bookmark.path.length > 1
      ? bookmark.path.slice(1).join(" / ")
      : (bookmark.path[0] || "未分类");
    if (!grouped.has(groupName)) grouped.set(groupName, []);
    grouped.get(groupName).push(bookmark);
  }

  let globalIndex = 0;
  for (const [groupName, items] of grouped) {
    const group = document.createElement("section");
    group.className = "bookmark-group";
    group.dataset.parentId = items[0].parentId;
    const heading = document.createElement("div");
    heading.className = "bookmark-group-heading";
    const title = document.createElement("h3");
    title.textContent = groupName;
    const count = document.createElement("span");
    count.textContent = `${items.length}`;
    heading.append(title, count);
    const list = document.createElement("div");
    list.className = "bookmark-list";
    list.addEventListener("dragover", (event) => {
      if (!draggedBookmark) return;
      event.preventDefault();
      event.dataTransfer.dropEffect = "move";
      group.classList.add("is-drop-target");
    });
    list.addEventListener("dragleave", (event) => {
      if (!list.contains(event.relatedTarget)) group.classList.remove("is-drop-target");
    });
    list.addEventListener("drop", async (event) => {
      if (!draggedBookmark || event.target.closest(".bookmark-link")) return;
      event.preventDefault();
      await moveBookmarkTo(draggedBookmark, items[0].parentId);
    });
    items.forEach((bookmark) => list.append(createBookmarkLink(bookmark, globalIndex++)));
    group.append(heading, list);
    elements.bookmarkGroups.append(group);
  }
}

function matches(item, query) {
  const text = `${item.title || item.name || ""} ${item.url || ""} ${(item.path || []).join(" ")}`.toLowerCase();
  return text.includes(query.toLowerCase());
}

async function collectSearchResults(query) {
  if (!query) return [];
  const [tabs, history] = await Promise.all([
    chrome.tabs.query({}),
    chrome.history.search({ text: query, maxResults: 12, startTime: 0 })
  ]);
  const groups = [
    { label: "已打开的标签页", type: "tab", items: tabs.filter((item) => matches(item, query)).slice(0, 8) },
    { label: "收藏夹", type: "bookmark", items: bookmarks.filter((item) => matches(item, query)).slice(0, 8) },
    { label: "我的网站", type: "custom", items: preferences.customSites.filter((item) => matches(item, query)).slice(0, 6) },
    { label: "历史记录", type: "history", items: history.slice(0, 8) }
  ];
  const seen = new Set();
  return groups.flatMap((group) => group.items.flatMap((item) => {
    const key = group.type === "tab" ? `tab:${item.id}` : item.url;
    if (!item.url || seen.has(key)) return [];
    seen.add(key);
    return [{ ...item, group: group.label, type: group.type }];
  }));
}

function renderSearchResults(results) {
  elements.searchResults.replaceChildren();
  activeResults = results;
  selectedResultIndex = results.length ? 0 : -1;
  if (!results.length) {
    elements.searchResults.classList.add("hidden");
    return;
  }

  let currentGroup = "";
  results.forEach((result, index) => {
    if (result.group !== currentGroup) {
      currentGroup = result.group;
      const label = document.createElement("div");
      label.className = "result-group-label";
      label.textContent = currentGroup;
      elements.searchResults.append(label);
    }
    const row = document.createElement("button");
    row.type = "button";
    row.className = `result-row${index === selectedResultIndex ? " is-active" : ""}`;
    row.dataset.index = String(index);
    row.setAttribute("role", "option");
    row.append(createFavicon(result.url, 28));
    const copy = document.createElement("span");
    copy.className = "result-copy";
    const title = document.createElement("div");
    title.className = "result-title";
    title.textContent = result.title || result.name || result.url;
    const url = document.createElement("div");
    url.className = "result-url";
    url.textContent = result.url;
    copy.append(title, url);
    const badge = document.createElement("span");
    badge.className = "result-badge";
    badge.textContent = index === selectedResultIndex ? "Enter" : "";
    row.append(copy, badge);
    row.addEventListener("click", () => openResult(result));
    elements.searchResults.append(row);
  });
  elements.searchResults.classList.remove("hidden");
}

async function openResult(result) {
  if (result.type === "tab") {
    await chrome.tabs.update(result.id, { active: true });
    if (result.windowId) await chrome.windows.update(result.windowId, { focused: true });
    return;
  }
  const url = result.type === "bookmark"
    ? resolveMappedUrl(result.url, preferences.prefixRules, result.ancestorIds)
    : result.url;
  await chrome.tabs.create({ url });
}

const runSearch = debounce(async () => {
  renderSearchResults(await collectSearchResults(elements.searchInput.value.trim()));
}, 120);

function moveSelection(direction) {
  if (!activeResults.length) return;
  selectedResultIndex = (selectedResultIndex + direction + activeResults.length) % activeResults.length;
  document.querySelectorAll(".result-row").forEach((row) => {
    const active = Number(row.dataset.index) === selectedResultIndex;
    row.classList.toggle("is-active", active);
    row.querySelector(".result-badge").textContent = active ? "Enter" : "";
    if (active) row.scrollIntoView({ block: "nearest" });
  });
}

async function refresh() {
  preferences = await getPreferences();
  const [tree, frequent] = await Promise.all([
    getBookmarkTree(),
    preferences.showFrequent ? getFrequentPages(preferences.frequentLimit) : Promise.resolve([])
  ]);
  const mainFolder = findMainBookmarkFolder(tree, preferences.bookmarkRootId);
  bookmarks = flattenBookmarks(mainFolder, []);
  frequentPages = frequent;
  renderFrequent();
  renderCustomSites();
  renderBookmarks();
}

elements.searchInput.addEventListener("input", runSearch);
elements.searchInput.addEventListener("keydown", (event) => {
  if (event.key === "ArrowDown") {
    event.preventDefault();
    moveSelection(1);
  } else if (event.key === "ArrowUp") {
    event.preventDefault();
    moveSelection(-1);
  } else if (event.key === "Escape") {
    elements.searchInput.value = "";
    renderSearchResults([]);
  }
});
elements.searchForm.addEventListener("submit", (event) => {
  event.preventDefault();
  if (selectedResultIndex >= 0) {
    openResult(activeResults[selectedResultIndex]);
    return;
  }
  const query = elements.searchInput.value.trim();
  if (!query) return;
  const url = looksLikeUrl(query) ? toNavigableUrl(query) : getSearchUrl(query, preferences.searchEngine);
  chrome.tabs.create({ url });
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !elements.contextMenu.classList.contains("hidden")) {
    hideBookmarkContextMenu();
    return;
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
    event.preventDefault();
    elements.searchInput.focus();
    elements.searchInput.select();
  }
});
document.addEventListener("click", (event) => {
  if (!elements.searchForm.contains(event.target) && !elements.searchResults.contains(event.target)) {
    elements.searchResults.classList.add("hidden");
  }
});
elements.manageSites.addEventListener("click", () => chrome.tabs.create({ url: chrome.runtime.getURL("pages/settings/index.html#sites") }));
elements.contextMenu.addEventListener("click", (event) => {
  const actionButton = event.target.closest("[data-action]");
  if (actionButton) executeBookmarkAction(actionButton.dataset.action);
});
document.addEventListener("click", (event) => {
  if (!elements.contextMenu.contains(event.target)) hideBookmarkContextMenu();
});
document.addEventListener("contextmenu", (event) => {
  if (!event.target.closest(".bookmark-link")) hideBookmarkContextMenu();
});
elements.bookmarkEditForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!editingBookmark) return;
  const title = elements.bookmarkEditTitle.value.trim();
  const url = elements.bookmarkEditUrl.value.trim();
  if (!title || !/^https?:\/\//i.test(url)) {
    showToast("请填写有效的名称和 HTTP(S) 网址", "error");
    return;
  }
  try {
    await chrome.bookmarks.update(editingBookmark.id, { title, url });
    elements.bookmarkEditDialog.close();
    editingBookmark = null;
    await refresh();
    showToast("书签已更新");
  } catch (error) {
    showToast(error.message || "更新失败", "error");
  }
});
elements.cancelBookmarkEdit.addEventListener("click", () => elements.bookmarkEditDialog.close());
elements.closeBookmarkEdit.addEventListener("click", () => elements.bookmarkEditDialog.close());
elements.qrImage.addEventListener("load", () => {
  elements.qrLoading.classList.add("hidden");
  elements.qrImage.classList.remove("hidden");
});
elements.qrImage.addEventListener("error", () => {
  elements.qrLoading.textContent = "二维码生成失败，请检查网络后重试";
  elements.qrLoading.classList.remove("hidden");
  elements.qrImage.classList.add("hidden");
});
elements.closeQr.addEventListener("click", () => elements.qrDialog.close());
watchPreferences((next) => {
  preferences = next;
  refresh().catch(() => showToast("刷新设置失败", "error"));
});

const refreshBookmarks = debounce(() => {
  refresh().catch(() => showToast("收藏夹刷新失败", "error"));
}, 350);
chrome.bookmarks.onCreated.addListener(refreshBookmarks);
chrome.bookmarks.onRemoved.addListener(refreshBookmarks);
chrome.bookmarks.onChanged.addListener(refreshBookmarks);
chrome.bookmarks.onMoved.addListener(refreshBookmarks);

refresh().catch((error) => showToast(error.message || "加载数据失败", "error"));
