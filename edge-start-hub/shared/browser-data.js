const BLOCKED_PROTOCOLS = /^(edge|chrome|about|chrome-extension|extension):/i;

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
