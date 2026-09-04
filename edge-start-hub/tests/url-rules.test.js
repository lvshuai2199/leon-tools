import test from "node:test";
import assert from "node:assert/strict";
import { calculateBookmarkUpdates, normalizePrefix, resolveMappedUrl } from "../shared/url-rules.js";

test("normalizes trailing slashes", () => {
  assert.equal(normalizePrefix(" http://10.0.0.1:8080/// "), "http://10.0.0.1:8080");
  assert.equal(normalizePrefix("10.0.0.1:8080/*"), "http://10.0.0.1:8080");
});

test("maps the longest applicable prefix and keeps the path", () => {
  const rules = [{
    enabled: true,
    folderId: "server-folder",
    sourcePrefixes: ["http://10.0.0.1", "http://10.0.0.1:8080/app"],
    targetPrefix: "https://server.example/app"
  }];
  const result = resolveMappedUrl(
    "http://10.0.0.1:8080/app/dashboard?id=3",
    rules,
    ["favorites", "server-folder"]
  );
  assert.equal(result, "https://server.example/app/dashboard?id=3");
});

test("does not apply a folder rule outside that folder", () => {
  const rules = [{
    enabled: true,
    folderId: "server-folder",
    sourcePrefixes: ["http://10.0.0.1"],
    targetPrefix: "http://10.0.0.2"
  }];
  assert.equal(resolveMappedUrl("http://10.0.0.1/a", rules, ["other"]), "http://10.0.0.1/a");
});

test("creates bookmark updates only for changed URLs", () => {
  const bookmarks = [
    { id: "1", title: "Admin", url: "http://old.local/admin", ancestorIds: ["servers"] },
    { id: "2", title: "Docs", url: "https://example.com", ancestorIds: ["servers"] }
  ];
  const rules = [{
    enabled: true,
    folderId: "servers",
    sourcePrefixes: ["http://old.local"],
    targetPrefix: "http://new.local"
  }];
  assert.deepEqual(calculateBookmarkUpdates(bookmarks, rules), [{
    id: "1",
    title: "Admin",
    oldUrl: "http://old.local/admin",
    newUrl: "http://new.local/admin"
  }]);
});

test("accepts comma-separated and bare-host rule data", () => {
  const rules = [{
    enabled: true,
    sourcePrefixes: "10.0.0.1:8080, old-server.local",
    targetPrefix: "10.0.0.2:8080"
  }];
  assert.equal(resolveMappedUrl("http://10.0.0.1:8080/app", rules), "http://10.0.0.2:8080/app");
  assert.equal(resolveMappedUrl("http://old-server.local/docs", rules), "http://10.0.0.2:8080/docs");
});
