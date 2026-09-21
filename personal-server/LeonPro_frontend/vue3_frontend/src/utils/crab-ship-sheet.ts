const WIDTH = 1080;
const PAD = 48;

export type ShipSheetRow = {
  seqNo?: number | string;
  shipDate?: string;
  customerName?: string;
  phone?: string;
  address?: string;
  spec?: string;
  quantity?: number | null;
  trackingNo?: string;
  paid?: number | boolean;
  shipped?: number | boolean;
};

function wrapText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number) {
  const source = String(text || "").trim() || "—";
  const lines: string[] = [];
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

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  const radius = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.arcTo(x + w, y, x + w, y + h, radius);
  ctx.arcTo(x + w, y + h, x, y + h, radius);
  ctx.arcTo(x, y + h, x, y, radius);
  ctx.arcTo(x, y, x + w, y, radius);
  ctx.closePath();
}

function pill(ctx: CanvasRenderingContext2D, x: number, y: number, text: string, bg: string, fg: string) {
  ctx.font = "600 28px 'PingFang SC','Microsoft YaHei',sans-serif";
  const w = ctx.measureText(text).width + 32;
  roundRect(ctx, x, y, w, 44, 22);
  ctx.fillStyle = bg;
  ctx.fill();
  ctx.fillStyle = fg;
  ctx.fillText(text, x + 16, y + 32);
  return w + 12;
}

function measureCard(ctx: CanvasRenderingContext2D, item: ShipSheetRow, inner: number) {
  ctx.font = "600 36px 'PingFang SC','Microsoft YaHei',sans-serif";
  const addr = wrapText(ctx, item.address || "", inner);
  ctx.font = "500 32px 'PingFang SC','Microsoft YaHei',sans-serif";
  const track = wrapText(ctx, item.trackingNo ? `快递 ${item.trackingNo}` : "快递单号未填", inner);
  return 56 + 52 + 44 + addr.length * 48 + 16 + track.length * 42 + 36;
}

export function renderShipSheet(records: ShipSheetRow[], options: { date?: string } = {}) {
  const list = records.filter(Boolean);
  const date = options.date || list[0]?.shipDate || "";
  const canvas = document.createElement("canvas");
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("无法绘制发货图");
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
    wrapText(ctx, item.address || "", inner).forEach((line) => {
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

export function canvasToBlob(canvas: HTMLCanvasElement) {
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob);
      else reject(new Error("生成图片失败"));
    }, "image/png");
  });
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

export async function saveSheetToAlbum(blob: Blob, filename: string) {
  const file = new File([blob], filename, { type: "image/png" });
  if (navigator.canShare && navigator.canShare({ files: [file] })) {
    await navigator.share({ files: [file], title: "发货清单", text: "存入相册或发给仓库" });
    return "share";
  }
  downloadBlob(blob, filename);
  return "download";
}
