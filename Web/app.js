const state = { renameFiles: [], renameEntries: [], dedupeReport: "", filterReport: "", imageReport: "" };

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const esc = (value) => String(value).replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" }[char]));
const fileName = (file) => file.webkitRelativePath || file.name;

function downloadBlob(name, blob) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function downloadText(name, text, type = "text/plain;charset=utf-8") {
  downloadBlob(name, new Blob([text], { type }));
}

function setResult(id, html) {
  const target = document.getElementById(id);
  if (target) target.innerHTML = html;
}

function showTool(tool) {
  const panelMap = { rename: "files", dedupe: "files", filter: "files", pdf: "documents", markdown: "documents", "image-check": "documents", trace: "trace" };
  const section = panelMap[tool] || tool;
  $$(".page-section").forEach((panel) => panel.classList.toggle("is-visible", panel.dataset.panel === section));
  $$(".nav-item").forEach((item) => item.classList.toggle("is-active", item.dataset.tool === section));
  if (["rename", "dedupe", "filter"].includes(tool)) activateSubtool("files", tool);
  if (["pdf", "markdown", "image-check"].includes(tool)) activateSubtool("documents", tool);
  const titles = { overview: "工具工作台", files: "文件工具", documents: "文档工具", trace: "机器人轨迹分析" };
  $("#page-title").textContent = titles[section] || titles.overview;
}

function activateSubtool(section, tool) {
  const panel = $(`[data-panel="${section}"]`);
  $$(".subnav-item", panel).forEach((item) => item.classList.toggle("is-active", item.dataset.subtool === tool));
  $$(".tool-panel", panel).forEach((item) => item.classList.toggle("is-visible", item.dataset.toolPanel === tool));
}

function renderRenamePreview() {
  state.renameFiles = [...$("#rename-files").files];
  const prefix = $("#rename-prefix").value || "file-";
  const start = Number.parseInt($("#rename-start").value, 10) || 0;
  state.renameEntries = state.renameFiles.map((file, index) => {
    const extension = file.name.includes(".") ? `.${file.name.split(".").pop()}` : "";
    return { file, name: `${prefix}${start + index}${extension}` };
  });
  if (!state.renameEntries.length) {
    setResult("rename-result", '<span class="empty-state">请先选择至少一个文件。</span>');
    $("#rename-export").disabled = true;
    return;
  }
  const rows = state.renameEntries.slice(0, 80).map(({ file, name }) => `<li>${esc(file.name)} <span class="muted">→</span> <strong>${esc(name)}</strong></li>`).join("");
  const suffix = state.renameEntries.length > 80 ? `<li>… 还有 ${state.renameEntries.length - 80} 个文件</li>` : "";
  setResult("rename-result", `<div class="result-summary"><div><strong>${state.renameEntries.length}</strong><span>个文件</span></div><div><strong class="ok">可导出</strong><span>原文件不会被改动</span></div></div><ol class="result-list">${rows}${suffix}</ol>`);
  $("#rename-export").disabled = false;
}

async function exportRenamedFiles() {
  if (!state.renameEntries.length || !window.JSZip) return;
  const zip = new JSZip();
  for (const entry of state.renameEntries) zip.file(entry.name, await entry.file.arrayBuffer());
  downloadBlob(`renamed-${new Date().toISOString().slice(0, 10)}.zip`, await zip.generateAsync({ type: "blob" }));
}

async function digest(file) {
  const buffer = await file.arrayBuffer();
  const bytes = await crypto.subtle.digest("SHA-256", buffer);
  return [...new Uint8Array(bytes)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function runDedupe() {
  const files = [...$("#dedupe-files").files];
  if (!files.length) return setResult("dedupe-result", '<span class="empty-state">请先选择文件或文件夹。</span>');
  setResult("dedupe-result", '<span class="empty-state">正在计算文件指纹，请稍候…</span>');
  const hashes = new Map();
  for (const file of files) {
    const hash = await digest(file);
    if (!hashes.has(hash)) hashes.set(hash, []);
    hashes.get(hash).push(fileName(file));
  }
  const groups = [...hashes.values()].filter((items) => items.length > 1);
  state.dedupeReport = ["LeonTools 文件去重报告", `生成时间: ${new Date().toLocaleString()}`, `扫描文件: ${files.length}`, `重复组: ${groups.length}`, "", ...groups.map((items, index) => `重复组 ${index + 1}\n${items.map((item) => `- ${item}`).join("\n")}`)].join("\n");
  const duplicateCount = groups.reduce((sum, items) => sum + items.length - 1, 0);
  const list = groups.slice(0, 30).map((items) => `<li>${items.map(esc).join(' <span class="muted">↔</span> ')}</li>`).join("");
  setResult("dedupe-result", `<div class="result-summary"><div><strong>${files.length}</strong><span>已扫描</span></div><div><strong class="${duplicateCount ? "warn" : "ok"}">${duplicateCount}</strong><span>可移除副本</span></div><div><strong>${groups.length}</strong><span>重复组</span></div></div>${groups.length ? `<ol class="result-list">${list}</ol>` : '<p class="ok">没有发现内容相同的文件。</p>'}`);
  $("#dedupe-report").disabled = false;
}

function runFilter() {
  const files = [...$("#filter-files").files];
  const source = $("#filter-pattern").value.trim();
  if (!files.length) return setResult("filter-result", '<span class="empty-state">请先选择文件。</span>');
  let pattern;
  try { pattern = new RegExp(source, "i"); } catch (error) { return setResult("filter-result", `<p class="danger">正则表达式无效：${esc(error.message)}</p>`); }
  const matched = files.map(fileName).filter((name) => pattern.test(name));
  state.filterReport = ["LeonTools 文件筛选清单", `筛选条件: ${source}`, "", ...matched].join("\n");
  setResult("filter-result", `<div class="result-summary"><div><strong>${matched.length}</strong><span>匹配文件</span></div><div><strong>${files.length}</strong><span>总文件</span></div></div>${matched.length ? `<ol class="result-list">${matched.slice(0, 100).map((name) => `<li>${esc(name)}</li>`).join("")}</ol>` : '<p class="muted">没有匹配项。</p>'}`);
  $("#filter-report").disabled = false;
}

async function mergePdfs() {
  const files = [...$("#pdf-files").files];
  if (!files.length) return setResult("pdf-result", '<span class="empty-state">请先选择 PDF 文件。</span>');
  if (!window.PDFLib) return setResult("pdf-result", '<p class="danger">pdf-lib 未加载，请检查网络后刷新页面。</p>');
  setResult("pdf-result", '<span class="empty-state">正在合并 PDF…</span>');
  try {
    const merged = await PDFLib.PDFDocument.create();
    let pages = 0;
    for (const file of files) {
      const source = await PDFLib.PDFDocument.load(await file.arrayBuffer());
      const copied = await merged.copyPages(source, source.getPageIndices());
      copied.forEach((page) => merged.addPage(page));
      pages += copied.length;
    }
    downloadBlob(`merged-${new Date().toISOString().slice(0, 10)}.pdf`, new Blob([await merged.save()], { type: "application/pdf" }));
    setResult("pdf-result", `<div class="result-summary"><div><strong>${files.length}</strong><span>个 PDF</span></div><div><strong class="ok">${pages}</strong><span>页已合并并下载</span></div></div><p class="ok">处理完成。文件顺序与选择顺序一致。</p>`);
  } catch (error) {
    setResult("pdf-result", `<p class="danger">合并失败：${esc(error.message)}</p>`);
  }
}

function renderMarkdown() {
  const source = $("#markdown-input").value;
  $("#markdown-preview").innerHTML = window.marked ? marked.parse(source) : `<pre>${esc(source)}</pre>`;
}

function exportMarkdown() {
  const source = $("#markdown-input").value;
  const content = window.marked ? marked.parse(source) : `<pre>${esc(source)}</pre>`;
  const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>LeonTools Markdown Export</title><style>body{max-width:850px;margin:40px auto;padding:0 20px;font:16px/1.7 system-ui;color:#29373d}pre{background:#14242c;color:#fff;padding:16px;overflow:auto}code{font-family:Consolas,monospace}img{max-width:100%}</style></head><body>${content}</body></html>`;
  downloadText("markdown-export.html", html, "text/html;charset=utf-8");
}

async function loadMarkdownFile() {
  const file = $("#markdown-file").files[0];
  if (file) { $("#markdown-input").value = await file.text(); renderMarkdown(); }
}

async function runImageCheck() {
  const files = [...$("#image-check-files").files];
  const markdownFiles = files.filter((file) => /\.(md|markdown)$/i.test(file.name));
  const imageFiles = files.filter((file) => /\.(png|jpe?g|gif|bmp|svg|webp)$/i.test(file.name));
  if (!files.length) return setResult("image-check-result", '<span class="empty-state">请先选择包含 Markdown 和图片的文件夹。</span>');
  const imageNames = new Set(imageFiles.map((file) => file.name.toLowerCase()));
  const referenced = new Set();
  const missing = [];
  for (const file of markdownFiles) {
    const content = await file.text();
    const matches = [...content.matchAll(/!\[[^\]]*]\(([^)]+)\)/g)];
    for (const match of matches) {
      const raw = match[1].split("?")[0].split("#")[0].replaceAll("\\", "/");
      const name = raw.split("/").pop().toLowerCase();
      if (imageNames.has(name)) referenced.add(name);
      else missing.push(`${fileName(file)} -> ${raw}`);
    }
  }
  const unused = [...imageNames].filter((name) => !referenced.has(name));
  state.imageReport = ["LeonTools 图片引用检查报告", `Markdown 文件: ${markdownFiles.length}`, `图片文件: ${imageFiles.length}`, "", "无法找到的引用:", ...missing.map((item) => `- ${item}`), "", "未被引用的图片:", ...unused.map((item) => `- ${item}`)].join("\n");
  setResult("image-check-result", `<div class="result-summary"><div><strong>${markdownFiles.length}</strong><span>Markdown 文件</span></div><div><strong class="${missing.length ? "warn" : "ok"}">${missing.length}</strong><span>失效引用</span></div><div><strong class="${unused.length ? "warn" : "ok"}">${unused.length}</strong><span>未引用图片</span></div></div>${missing.length ? `<p class="danger">失效引用</p><ul class="result-list">${missing.slice(0, 40).map((item) => `<li>${esc(item)}</li>`).join("")}</ul>` : '<p class="ok">没有发现失效图片引用。</p>'}${unused.length ? `<p class="warn">未引用图片</p><ul class="result-list">${unused.slice(0, 40).map((item) => `<li>${esc(item)}</li>`).join("")}</ul>` : ""}`);
  $("#image-check-report").disabled = false;
}

function bindEvents() {
  $$(".nav-item").forEach((item) => item.addEventListener("click", () => showTool(item.dataset.tool)));
  $$("[data-open-tool]").forEach((item) => item.addEventListener("click", () => showTool(item.dataset.openTool)));
  $$(".subnav-item").forEach((item) => item.addEventListener("click", () => activateSubtool(item.closest(".page-section").dataset.panel, item.dataset.subtool)));
  $("#reset-workspace").addEventListener("click", () => { location.reload(); });
  $("#rename-preview").addEventListener("click", renderRenamePreview);
  $("#rename-export").addEventListener("click", exportRenamedFiles);
  $("#dedupe-run").addEventListener("click", runDedupe);
  $("#dedupe-report").addEventListener("click", () => downloadText("dedupe-report.txt", state.dedupeReport));
  $("#filter-run").addEventListener("click", runFilter);
  $("#filter-report").addEventListener("click", () => downloadText("file-filter-report.txt", state.filterReport));
  $("#pdf-run").addEventListener("click", mergePdfs);
  $("#markdown-input").addEventListener("input", renderMarkdown);
  $("#markdown-load").addEventListener("click", () => $("#markdown-file").click());
  $("#markdown-file").addEventListener("change", loadMarkdownFile);
  $("#markdown-export").addEventListener("click", exportMarkdown);
  $("#image-check-run").addEventListener("click", runImageCheck);
  $("#image-check-report").addEventListener("click", () => downloadText("image-reference-report.txt", state.imageReport));
  renderMarkdown();
}

document.addEventListener("DOMContentLoaded", () => {
  if (window.lucide) lucide.createIcons();
  bindEvents();
});
