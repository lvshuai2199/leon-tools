const WIDTH = 1080;
const PAD = 48;

function wrapText(ctx, text, maxWidth) {
  const source = String(text || "").trim() || "—";
  const lines = [];
  let line = "";
  for (const ch of source) {
    const next = line + ch;
    if (ctx.measureText(next).width > maxWidth && line) {
      lines.push(line);
      line = ch;
    } else {
      line = next;
    }
  }
  if (line) lines.push(line);
  return lines;
}

function roundRect(ctx, x, y, w, h, r) {
  const radius = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.arcTo(x + w, y, x + w, y + h, radius);
  ctx.arcTo(x + w, y + h, x, y + h, radius);
  ctx.arcTo(x, y + h, x, y, radius);
  ctx.arcTo(x, y, x + w, y, radius);
  ctx.closePath();
}

function pill(ctx, x, y, text, bg, fg) {
  ctx.font = "600 28px 'PingFang SC','Microsoft YaHei',sans-serif";
  const w = ctx.measureText(text).width + 32;
  roundRect(ctx, x, y, w, 44, 22);
  ctx.fillStyle = bg;
  ctx.fill();
  ctx.fillStyle = fg;
  ctx.fillText(text, x + 16, y + 32);
  return w + 12;
}

function measureCard(ctx, item, inner) {
  ctx.font = "600 36px 'PingFang SC','Microsoft YaHei',sans-serif";
  const addr = wrapText(ctx, item.address, inner);
  ctx.font = "500 32px 'PingFang SC','Microsoft YaHei',sans-serif";
  const track = wrapText(ctx, item.trackingNo ? `快递 ${item.trackingNo}` : "快递单号未填", inner);
  return 56 + 52 + 44 + addr.length * 48 + 16 + track.length * 42 + 36;
}

export function renderShipSheet(records, options = {}) {
  const list = Array.isArray(records) ? records.filter(Boolean) : [];
  const date = options.date || list[0]?.shipDate || "";
  const canvas = document.createElement("canvas");
  const ctx = canvas.getContext("2d");
  const inner = WIDTH - PAD * 2 - 48;
  ctx.font = "600 36px 'PingFang SC','Microsoft YaHei',sans-serif";
  const cardHeights = list.map((item) => measureCard(ctx, item, inner));
  const totalQty = list.reduce((sum, item) => sum + (Number(item.quantity) || 0), 0);
  const height = PAD + 168 + cardHeights.reduce((sum, h) => sum + h + 20, 0) + 36;
  canvas.width = WIDTH;
  canvas.height = Math.max(height, 640);

  ctx.fillStyle = "#f3f4f6";
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  roundRect(ctx, PAD, PAD, WIDTH - PAD * 2, 140, 24);
  ctx.fillStyle = "#1d4ed8";
  ctx.fill();
  ctx.fillStyle = "#fff";
  ctx.font = "700 48px 'PingFang SC','Microsoft YaHei',sans-serif";
  ctx.fillText("螃蟹发货清单", PAD + 36, PAD + 62);
  ctx.font = "500 28px 'PingFang SC','Microsoft YaHei',sans-serif";
  ctx.fillStyle = "#dbeafe";
  ctx.fillText(`${date || "未填日期"}  ·  ${list.length} 单  ·  合计 ${totalQty} 只`, PAD + 36, PAD + 110);

  let y = PAD + 168;
  list.forEach((item, index) => {
    const h = cardHeights[index];
    roundRect(ctx, PAD, y, WIDTH - PAD * 2, h, 20);
    ctx.fillStyle = "#fff";
    ctx.fill();
    ctx.strokeStyle = "#e5e7eb";
    ctx.lineWidth = 2;
    ctx.stroke();

    const x = PAD + 28;
    let cursor = y + 56;
    ctx.fillStyle = "#111827";
    ctx.font = "700 40px 'PingFang SC','Microsoft YaHei',sans-serif";
    const seq = item.seqNo || index + 1;
    ctx.fillText(`${seq}. ${item.customerName || "未填姓名"}`, x, cursor);
    ctx.fillStyle = "#c2410c";
    ctx.font = "700 34px 'PingFang SC','Microsoft YaHei',sans-serif";
    const qtyText = `${item.spec || "-"}  ×  ${item.quantity || 0}只`;
    const qtyW = ctx.measureText(qtyText).width;
    ctx.fillText(qtyText, WIDTH - PAD - 28 - qtyW, cursor);

    cursor += 48;
    ctx.fillStyle = "#374151";
    ctx.font = "500 32px 'PingFang SC','Microsoft YaHei',sans-serif";
    ctx.fillText(item.phone || "无电话", x, cursor);

    cursor += 18;
    let px = x;
    px += pill(ctx, px, cursor, item.paid ? "已付款" : "未付款", item.paid ? "#dcfce7" : "#f3f4f6", item.paid ? "#166534" : "#6b7280");
    pill(ctx, px, cursor, item.shipped ? "已发货" : "未发货", item.shipped ? "#dbeafe" : "#f3f4f6", item.shipped ? "#1d4ed8" : "#6b7280");

    cursor += 68;
    ctx.fillStyle = "#111827";
    ctx.font = "600 36px 'PingFang SC','Microsoft YaHei',sans-serif";
    wrapText(ctx, item.address, inner).forEach((line) => {
      ctx.fillText(line, x, cursor);
      cursor += 48;
    });

    ctx.fillStyle = item.trackingNo ? "#1d4ed8" : "#9ca3af";
    ctx.font = "600 32px 'PingFang SC','Microsoft YaHei',sans-serif";
    wrapText(ctx, item.trackingNo ? `快递 ${item.trackingNo}` : "快递单号未填", inner).forEach((line) => {
      ctx.fillText(line, x, cursor);
      cursor += 42;
    });

    y += h + 20;
  });

  return canvas;
}

export function canvasToBlob(canvas) {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob);
      else reject(new Error("生成图片失败"));
    }, "image/png");
  });
}

export async function saveSheetToAlbum(blob, filename) {
  const file = new File([blob], filename, { type: "image/png" });
  if (navigator.canShare && navigator.canShare({ files: [file] })) {
    await navigator.share({ files: [file], title: "发货清单", text: "存入相册或发给仓库" });
    return "share";
  }
  downloadBlob(blob, filename);
  return "download";
}

export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

export function showSheetPreview(blob, options = {}) {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(blob);
    const filename = options.filename || "螃蟹发货清单.png";
    const root = document.createElement("div");
    root.className = "ship-sheet-root";
    root.innerHTML = `
      <div class="ship-sheet-mask">
        <div class="ship-sheet-panel">
          <div class="ship-sheet-title">发货清单预览</div>
          <p class="ship-sheet-hint">存入相册：手机可走系统分享里的「存储图像」。也可长按图片保存。</p>
          <div class="ship-sheet-scroll"><img class="ship-sheet-img" alt="发货清单" /></div>
          <div class="ship-sheet-actions">
            <button type="button" data-album>存入相册</button>
            <button type="button" class="ghost" data-down>下载图片</button>
            <button type="button" class="ghost" data-close>关闭</button>
          </div>
        </div>
      </div>
    `;
    const style = document.createElement("style");
    style.textContent = PREVIEW_CSS;
    root.appendChild(style);
    const img = root.querySelector(".ship-sheet-img");
    img.src = url;
    const finish = () => {
      URL.revokeObjectURL(url);
      root.remove();
      resolve();
    };
    root.querySelector("[data-close]").addEventListener("click", finish);
    root.querySelector("[data-down]").addEventListener("click", () => downloadBlob(blob, filename));
    root.querySelector("[data-album]").addEventListener("click", async () => {
      try {
        const mode = await saveSheetToAlbum(blob, filename);
        if (mode === "download" && typeof options.onDownloadFallback === "function") {
          options.onDownloadFallback();
        }
      } catch (error) {
        if (error && error.name === "AbortError") return;
        downloadBlob(blob, filename);
        if (typeof options.onDownloadFallback === "function") options.onDownloadFallback();
      }
    });
    document.body.appendChild(root);
  });
}

const PREVIEW_CSS = `
.ship-sheet-root{position:fixed;inset:0;z-index:4200;}
.ship-sheet-mask{position:absolute;inset:0;background:rgba(15,23,42,.55);display:flex;align-items:flex-end;justify-content:center;}
.ship-sheet-panel{width:100%;max-width:560px;max-height:92vh;background:#fff;border-radius:16px 16px 0 0;padding:14px 14px 18px;display:flex;flex-direction:column;box-sizing:border-box;}
.ship-sheet-title{font-size:17px;font-weight:700;}
.ship-sheet-hint{margin:6px 0 10px;font-size:12px;color:#6b7280;line-height:1.5;}
.ship-sheet-scroll{overflow:auto;flex:1;min-height:180px;background:#f3f4f6;border-radius:12px;padding:8px;}
.ship-sheet-img{display:block;width:100%;border-radius:8px;}
.ship-sheet-actions{display:flex;gap:8px;margin-top:12px;}
.ship-sheet-actions button{flex:1;height:42px;border:none;border-radius:10px;background:#2563eb;color:#fff;font-size:14px;}
.ship-sheet-actions button.ghost{background:#e5e7eb;color:#111827;}
`;
