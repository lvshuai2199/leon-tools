import { getPreferences } from "./shared/storage.js";

chrome.runtime.onInstalled.addListener(({ reason }) => {
  if (reason === "install") {
    chrome.runtime.openOptionsPage();
  }
});

chrome.runtime.onStartup.addListener(async () => {
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
