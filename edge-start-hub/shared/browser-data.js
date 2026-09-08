const BLOCKED_PROTOCOLS = /^(edge|chrome|about|chrome-extension|extension):/i;
export const RECENT_TABS_STORAGE_KEY = "recentTabRecords";
const MAX_RECENT_TAB_RECORDS = 80;
let recentTabWriteQueue = Promise.resolve();

export function faviconUrl(pageUrl, size = 32) {
  return `${chrome.runtime.getURL("/_favicon/")}?pageUrl=${encodeURIComponent(pageUrl)}&size=${size}`;
}

export function isBrowsableUrl(url) {
  return Boolean(url) && !BLOCKED_PROTOCOLS.test(url);
}

export async function getFrequentPages(limit = 8) {
  const monthAgo = Date.now() - 30 * 24 * 60 * 60 * 1000;
  const history = await chrome.history.search({ text: "", startTime: monthAgo, maxResults: 1000 });
  const unique = new Map();

  for (const item of history) {
    if (!isBrowsableUrl(item.url)) continue;
    const score = (item.visitCount || 0) + (item.typedCount || 0) * 3;
    const current = unique.get(item.url);
    if (!current || score > current.score) {
      unique.set(item.url, { ...item, score });
    }
  }

  return [...unique.values()]
    .sort((a, b) => b.score - a.score || (b.lastVisitTime || 0) - (a.lastVisitTime || 0))
    .slice(0, limit);
}

export async function getRecentTabs(limit = 8) {
  const [tabs, stored] = await Promise.all([
    chrome.tabs.query({}),
    loadRecentTabRecords()
  ]);
  const openTabs = tabs
    .filter((tab) => isBrowsableUrl(tab.url))
    .sort((a, b) => (b.lastAccessed || 0) - (a.lastAccessed || 0)
      || (b.id || 0) - (a.id || 0)
      || (a.index || 0) - (b.index || 0));
  const openUrls = new Set(openTabs.map((tab) => tab.url));
  const remembered = stored
    .filter((item) => !openUrls.has(item.url))
    .map((item) => ({ ...item, isRemembered: true }));
  return [...openTabs, ...remembered]
    .sort((a, b) => (b.isRemembered ? (b.updatedAt || b.lastAccessed || 0) : (b.lastAccessed || 0))
      - (a.isRemembered ? (a.updatedAt || a.lastAccessed || 0) : (a.lastAccessed || 0))
      || (b.id || 0) - (a.id || 0))
    .slice(0, limit);
}

async function loadRecentTabRecords() {
  const storage = chrome.storage?.local;
  if (!storage) return [];
  const stored = await storage.get(RECENT_TABS_STORAGE_KEY);
  return Array.isArray(stored[RECENT_TABS_STORAGE_KEY])
    ? stored[RECENT_TABS_STORAGE_KEY].filter((item) => isBrowsableUrl(item?.url))
    : [];
}

export function rememberRecentTab(tab) {
  if (!isBrowsableUrl(tab?.url) || !chrome.storage?.local) return recentTabWriteQueue;
  recentTabWriteQueue = recentTabWriteQueue.catch(() => {}).then(async () => {
    const records = await loadRecentTabRecords();
    const record = {
      url: tab.url,
      title: tab.title || tab.url,
      lastAccessed: tab.lastAccessed || Date.now(),
      updatedAt: Date.now()
    };
    const next = [record, ...records.filter((item) => item.url !== record.url)]
      .sort((a, b) => (b.updatedAt || b.lastAccessed || 0) - (a.updatedAt || a.lastAccessed || 0))
      .slice(0, MAX_RECENT_TAB_RECORDS);
    await chrome.storage.local.set({
      [RECENT_TABS_STORAGE_KEY]: next
    });
  });
  return recentTabWriteQueue;
}

export async function syncRecentTabs() {
  const tabs = await chrome.tabs.query({});
  await Promise.all(tabs.map((tab) => rememberRecentTab(tab)));
  return tabs;
}

export function getSearchUrl(query, engine = "bing") {
  const encoded = encodeURIComponent(query);
  const engines = {
    bing: `https://www.bing.com/search?q=${encoded}`,
    google: `https://www.google.com/search?q=${encoded}`,
    baidu: `https://www.baidu.com/s?wd=${encoded}`,
    duckduckgo: `https://duckduckgo.com/?q=${encoded}`
  };
  return engines[engine] || engines.bing;
}

export function looksLikeUrl(value) {
  const text = value.trim();
  return /^https?:\/\//i.test(text) || /^[\w.-]+\.[a-z]{2,}(?:[/:?#]|$)/i.test(text);
}

export function toNavigableUrl(value) {
  return /^https?:\/\//i.test(value) ? value : `https://${value}`;
}
