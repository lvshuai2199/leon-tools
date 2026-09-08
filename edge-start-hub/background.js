import { rememberRecentTab, syncRecentTabs } from "./shared/browser-data.js";
import { getPreferences } from "./shared/storage.js";

const rememberTab = (tab) => {
  rememberRecentTab(tab).catch(() => {});
};

chrome.tabs.onCreated.addListener(rememberTab);
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.url || changeInfo.status === "complete") rememberTab(tab);
});
chrome.tabs.onActivated.addListener(({ tabId }) => {
  chrome.tabs.get(tabId).then(rememberTab).catch(() => {});
});

chrome.runtime.onInstalled.addListener(({ reason }) => {
  syncRecentTabs().catch(() => {});
  if (reason === "install") {
    chrome.runtime.openOptionsPage();
  }
});

chrome.runtime.onStartup.addListener(async () => {
  await syncRecentTabs();
  const preferences = await getPreferences();
  if (!preferences.openOnStartup) return;

  const newTabUrl = chrome.runtime.getURL("pages/newtab/index.html");
  const existing = (await chrome.tabs.query({})).filter((tab) => tab.url?.startsWith(newTabUrl));
  if (existing.length === 0) {
    await chrome.tabs.create({ url: newTabUrl });
  }
});

chrome.runtime.onMessage.addListener((message) => {
  if (message?.type === "open-shortcut-settings") {
    chrome.tabs.create({ url: "edge://extensions/shortcuts" });
  }
});
