export function normalizePrefix(value) {
  const cleaned = String(value || "")
    .trim()
    .replace(/[\u200B-\u200D\uFEFF]/g, "")
    .replace(/\*+$/, "")
    .replace(/\/+$/, "");
  if (cleaned && !/^[a-z][a-z\d+.-]*:\/\//i.test(cleaned) && /^[\w.-]+(?::\d+)?(?:\/|$)/.test(cleaned)) {
    return `http://${cleaned}`;
  }
  return cleaned;
}

export function isHttpPrefix(value) {
  try {
    const url = new URL(normalizePrefix(value));
    return (url.protocol === "http:" || url.protocol === "https:") && Boolean(url.host);
  } catch {
    return false;
  }
}

function prefixMatches(url, prefix) {
  const source = normalizePrefix(prefix);
  if (!source || !url) return false;
  if (url === source || url.startsWith(`${source}/`) || url.startsWith(`${source}?`) || url.startsWith(`${source}#`)) {
    return true;
  }

  // Treat default ports as equivalent so a server switch from http://host to
  // http://host:80 (or https://host to https://host:443) still maps cleanly.
  try {
    const sourceUrl = new URL(source);
    const candidateUrl = new URL(url);
    const sourcePort = sourceUrl.port || (sourceUrl.protocol === "http:" ? "80" : "443");
    const candidatePort = candidateUrl.port || (candidateUrl.protocol === "http:" ? "80" : "443");
    const sameOrigin = sourceUrl.protocol === candidateUrl.protocol
      && sourceUrl.hostname.toLowerCase() === candidateUrl.hostname.toLowerCase()
      && sourcePort === candidatePort;
    if (!sameOrigin) return false;
    const sourcePath = sourceUrl.pathname.replace(/\/+$/, "");
    return candidateUrl.pathname === sourcePath
      || (sourcePath === "" && candidateUrl.pathname.startsWith("/"))
      || candidateUrl.pathname.startsWith(`${sourcePath}/`);
  } catch {
    return false;
  }
}

function appliesToBookmark(rule, ancestorIds) {
  return !rule.folderId || ancestorIds.includes(rule.folderId);
}

export function resolveMappedUrl(url, rules = [], ancestorIds = []) {
  if (!url) return url;

  const matches = [];
  for (const rule of rules) {
    if (rule.enabled === false || !appliesToBookmark(rule, ancestorIds)) continue;
    const targetPrefix = normalizePrefix(rule.targetPrefix);
    if (!targetPrefix) continue;

    const sourcePrefixes = Array.isArray(rule.sourcePrefixes)
      ? rule.sourcePrefixes
      : String(rule.sourcePrefixes || "").split(/[\r\n,;]+/);
    for (const source of sourcePrefixes) {
      const sourcePrefix = normalizePrefix(source);
      if (sourcePrefix && sourcePrefix !== targetPrefix && prefixMatches(url, sourcePrefix)) {
        matches.push({ sourcePrefix, targetPrefix, sourceUrl: url });
      }
    }
  }

  matches.sort((a, b) => b.sourcePrefix.length - a.sourcePrefix.length);
  const match = matches[0];
  if (!match) return url;

  try {
    const sourceUrl = new URL(match.sourcePrefix);
    const candidateUrl = new URL(url);
    const targetUrl = new URL(match.targetPrefix);
    const sourcePath = sourceUrl.pathname.replace(/\/+$/, "");
    const suffix = candidateUrl.pathname.startsWith(sourcePath)
      ? candidateUrl.pathname.slice(sourcePath.length)
      : candidateUrl.pathname;
    targetUrl.pathname = `${targetUrl.pathname.replace(/\/+$/, "")}${suffix}` || "/";
    targetUrl.search = candidateUrl.search;
    targetUrl.hash = candidateUrl.hash;
    return targetUrl.toString().replace(/\/$/, (targetUrl.pathname === "/" ? "/" : ""));
  } catch {
    return `${match.targetPrefix}${url.slice(match.sourcePrefix.length)}`;
  }
}

export function calculateBookmarkUpdates(bookmarks, rules) {
  return bookmarks.flatMap((bookmark) => {
    const mappedUrl = resolveMappedUrl(bookmark.url, rules, bookmark.ancestorIds || []);
    return mappedUrl !== bookmark.url
      ? [{ id: bookmark.id, title: bookmark.title, oldUrl: bookmark.url, newUrl: mappedUrl }]
      : [];
  });
}
