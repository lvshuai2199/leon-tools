import assert from "node:assert/strict";
import test from "node:test";

const tabs = [
  { id: 1, index: 0, lastAccessed: 100, url: "https://older.example.com" },
  { id: 2, index: 1, lastAccessed: 300, url: "https://newer.example.com" },
  { id: 3, index: 2, lastAccessed: 400, url: "edge://settings" }
];

const storageData = {};

globalThis.chrome = {
  runtime: { getURL: (path) => `chrome-extension://test${path}` },
  tabs: { query: async () => tabs },
  storage: {
    local: {
      get: async (key) => ({ [key]: storageData[key] }),
      set: async (values) => Object.assign(storageData, values)
    }
  }
};

const { getRecentTabs, rememberRecentTab } = await import("../shared/browser-data.js");

test("recent tabs are sorted by last access and internal pages are excluded", async () => {
  assert.deepEqual((await getRecentTabs(2)).map((tab) => tab.id), [2, 1]);
});

test("remembered tabs remain available after the tab is closed", async () => {
  await rememberRecentTab({ url: "https://closed.example.com", title: "Closed tab" });
  const recent = await getRecentTabs(10);
  const remembered = recent.find((item) => item.url === "https://closed.example.com");
  assert.equal(remembered?.isRemembered, true);
});
