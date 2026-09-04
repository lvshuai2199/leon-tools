export const STORAGE_KEY = "startHubPreferences";

export const DEFAULT_PREFERENCES = Object.freeze({
  openOnStartup: false,
  showFrequent: true,
  showCustomSites: true,
  showBookmarks: true,
  frequentLimit: 8,
  bookmarkRootId: "",
  searchEngine: "bing",
  customSites: [],
  prefixRules: [],
  pinnedBookmarkIds: [],
  hiddenBookmarkIds: [],
  showHiddenBookmarks: false
});

export async function getPreferences() {
  const stored = await chrome.storage.local.get(STORAGE_KEY);
  return {
    ...DEFAULT_PREFERENCES,
    ...(stored[STORAGE_KEY] || {})
  };
}

export async function savePreferences(nextPreferences) {
  const normalized = {
    ...DEFAULT_PREFERENCES,
    ...nextPreferences
  };
  await chrome.storage.local.set({ [STORAGE_KEY]: normalized });
  return normalized;
}

export function watchPreferences(listener) {
  const handler = (changes, areaName) => {
    if (areaName !== "local" || !changes[STORAGE_KEY]) return;
    listener({
      ...DEFAULT_PREFERENCES,
      ...(changes[STORAGE_KEY].newValue || {})
    });
  };
  chrome.storage.onChanged.addListener(handler);
  return () => chrome.storage.onChanged.removeListener(handler);
}
