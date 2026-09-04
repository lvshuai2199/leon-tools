import { findMainBookmarkFolder, flattenBookmarks, getBookmarkTree } from "../../shared/bookmarks.js";
import { getFrequentPages, getSearchUrl, looksLikeUrl, toNavigableUrl } from "../../shared/browser-data.js";
import { getPreferences } from "../../shared/storage.js";
import { resolveMappedUrl } from "../../shared/url-rules.js";
import { createFavicon, debounce } from "../../shared/ui.js";

const elements = {
  searchForm: document.querySelector("#searchForm"),
  searchInput: document.querySelector("#searchInput"),
  statusLine: document.querySelector("#statusLine"),
  results: document.querySelector("#results")
};

let preferences;
let bookmarks = [];
let results = [];
let selectedIndex = 0;
let requestSequence = 0;

function matches(item, query) {
  const haystack = `${item.title || item.name || ""} ${item.url || ""} ${(item.path || []).join(" ")}`.toLowerCase();
  return haystack.includes(query.toLowerCase());
}

function deduplicateGroups(groups) {
  const seen = new Set();
  return groups.flatMap((group) => group.items.flatMap((item) => {
    if (!item.url) return [];
    const key = group.type === "tab" ? `tab:${item.id}` : item.url;
    if (seen.has(key)) return [];
    seen.add(key);
    return [{ ...item, type: group.type, group: group.label }];
  }));
}

async function search(query) {
  if (!query) {
    const frequent = await getFrequentPages(Math.min(preferences.frequentLimit, 8));
    return frequent.map((item) => ({ ...item, type: "history", group: "常用网页" }));
  }

  const [tabs, history] = await Promise.all([
    chrome.tabs.query({}),
    chrome.history.search({ text: query, maxResults: 15, startTime: 0 })
  ]);
  return deduplicateGroups([
    { label: "已打开的标签页", type: "tab", items: tabs.filter((item) => matches(item, query)).slice(0, 8) },
    { label: "收藏夹", type: "bookmark", items: bookmarks.filter((item) => matches(item, query)).slice(0, 8) },
    { label: "我的网站", type: "custom", items: preferences.customSites.filter((item) => matches(item, query)).slice(0, 6) },
    { label: "历史记录", type: "history", items: history.slice(0, 8) }
  ]);
}

function render() {
  elements.results.replaceChildren();
  elements.statusLine.textContent = elements.searchInput.value.trim() ? `${results.length} 个匹配结果` : "常用网页";
  if (!results.length) {
    const empty = document.createElement("div");
    empty.className = "empty";
    empty.textContent = "没有找到本地结果，按 Enter 使用默认搜索引擎检索网页";
    elements.results.append(empty);
    return;
  }

  let currentGroup = "";
  results.forEach((result, index) => {
    if (result.group !== currentGroup) {
      currentGroup = result.group;
      const label = document.createElement("div");
      label.className = "group-label";
      label.textContent = currentGroup;
      elements.results.append(label);
    }
    const row = document.createElement("button");
    row.type = "button";
    row.className = `result-row${index === selectedIndex ? " is-active" : ""}`;
    row.dataset.index = String(index);
    row.setAttribute("role", "option");
    row.setAttribute("aria-selected", String(index === selectedIndex));
    row.append(createFavicon(result.url, 24));
    const copy = document.createElement("span");
    copy.className = "result-copy";
    const title = document.createElement("div");
    title.className = "result-title";
    title.textContent = result.title || result.name || result.url;
    const url = document.createElement("div");
    url.className = "result-url";
    url.textContent = result.url;
    copy.append(title, url);
    const action = document.createElement("span");
    action.className = "result-action";
    action.textContent = index === selectedIndex ? "Enter" : "";
    row.append(copy, action);
    row.addEventListener("mousemove", () => {
      if (selectedIndex !== index) {
        selectedIndex = index;
        updateSelection();
      }
    });
    row.addEventListener("click", () => openResult(result));
    elements.results.append(row);
  });
}

function updateSelection() {
  elements.results.querySelectorAll(".result-row").forEach((row) => {
    const active = Number(row.dataset.index) === selectedIndex;
    row.classList.toggle("is-active", active);
    row.setAttribute("aria-selected", String(active));
    row.querySelector(".result-action").textContent = active ? "Enter" : "";
    if (active) row.scrollIntoView({ block: "nearest" });
  });
}

async function openResult(result) {
  if (result.type === "tab") {
    await chrome.tabs.update(result.id, { active: true });
    if (result.windowId) await chrome.windows.update(result.windowId, { focused: true });
    window.close();
    return;
  }
  const url = result.type === "bookmark"
    ? resolveMappedUrl(result.url, preferences.prefixRules, result.ancestorIds)
    : result.url;
  await chrome.tabs.create({ url });
  window.close();
}

const runSearch = debounce(async () => {
  const currentRequest = ++requestSequence;
  const nextResults = await search(elements.searchInput.value.trim());
  if (currentRequest !== requestSequence) return;
  results = nextResults;
  selectedIndex = 0;
  render();
}, 90);

elements.searchInput.addEventListener("input", runSearch);
elements.searchInput.addEventListener("keydown", (event) => {
  if (event.key === "ArrowDown" && results.length) {
    event.preventDefault();
    selectedIndex = (selectedIndex + 1) % results.length;
    updateSelection();
  } else if (event.key === "ArrowUp" && results.length) {
    event.preventDefault();
    selectedIndex = (selectedIndex - 1 + results.length) % results.length;
    updateSelection();
  } else if (event.key === "Escape") {
    window.close();
  }
});
elements.searchForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (results.length) {
    await openResult(results[selectedIndex]);
    return;
  }
  const query = elements.searchInput.value.trim();
  if (!query) return;
  const url = looksLikeUrl(query) ? toNavigableUrl(query) : getSearchUrl(query, preferences.searchEngine);
  await chrome.tabs.create({ url });
  window.close();
});
async function initialize() {
  const treePromise = getBookmarkTree();
  preferences = await getPreferences();
  const tree = await treePromise;
  bookmarks = flattenBookmarks(findMainBookmarkFolder(tree, preferences.bookmarkRootId), []);
  results = await search("");
  render();
  elements.searchInput.focus();
}

initialize().catch((error) => {
  elements.statusLine.textContent = "加载失败";
  elements.results.textContent = error.message;
});
