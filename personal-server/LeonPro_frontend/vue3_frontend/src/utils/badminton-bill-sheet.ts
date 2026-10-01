import { ballAmount, courtAmount, formatMoney, intVal, money, summarize, type BadmintonBallFeeItem, type BadmintonBillForm, type BadmintonCourtFeeItem } from "@/api/tool/badminton";
import { canvasToBlob, downloadBlob } from "@/utils/crab-ship-sheet";

const WIDTH = 1080;
const PAD = 48;
const HEADER_H = 148;
const FONT = "'PingFang SC','Microsoft YaHei',sans-serif";
const TEAL = "#0d9488";
const TEAL_DEEP = "#0f766e";
const TEAL_SOFT = "#e7f5f4";
const TEAL_SUB = "#d5f5f2";
const PAGE = "#f5f7fa";
const WHITE = "#ffffff";
const TEXT = "#1f2329";
const MUTED = "#6b7280";
const REGULAR = "#4b5563";
const PLACEHOLDER = "#a8abb2";
const BORDER = "#ebeef5";

type FeeLine = { main: string; note?: string; amount: string };

function wrapText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number) {
  const source = String(text || "");
  if (!source) return [] as string[];
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

function ellipsis(ctx: CanvasRenderingContext2D, text: string, maxWidth: number) {
  if (ctx.measureText(text).width <= maxWidth) return text;
  let s = text;
  while (s.length && ctx.measureText(`${s}…`).width > maxWidth) s = s.slice(0, -1);
  return `${s}…`;
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

function fillRound(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
  fill: string,
  stroke?: string
) {
  roundRect(ctx, x, y, w, h, r);
  ctx.fillStyle = fill;
  ctx.fill();
  if (stroke) {
    ctx.strokeStyle = stroke;
    ctx.lineWidth = 2;
    ctx.stroke();
  }
}

function courtLines(items: BadmintonCourtFeeItem[] | undefined): FeeLine[] {
  return (items || [])
    .filter(
      (item) => intVal(item.courtCount) || money(item.hours) || money(item.unitPrice) || (item.remark || "").trim()
    )
    .map((item) => ({
      main: `${intVal(item.courtCount)}片 × ${formatMoney(item.hours)}小时 × ${formatMoney(item.unitPrice)}元`,
      note: (item.remark || "").trim() || undefined,
      amount: formatMoney(courtAmount(item)),
    }));
}

function ballLines(items: BadmintonBallFeeItem[] | undefined): FeeLine[] {
  return (items || [])
    .filter((item) => (item.brand || "").trim() || intVal(item.quantity) || money(item.unitPrice))
    .map((item) => ({
      main: `${(item.brand || "").trim() || "未填品牌"} × ${intVal(item.quantity)} × ${formatMoney(item.unitPrice)}元`,
      amount: formatMoney(ballAmount(item)),
    }));
}

function measureLines(ctx: CanvasRenderingContext2D, lines: FeeLine[], inner: number) {
  if (!lines.length) return 52;
  let h = 0;
  lines.forEach((line) => {
    ctx.font = `500 32px ${FONT}`;
    const amountW = ctx.measureText(line.amount).width;
    const mainLines = wrapText(ctx, line.main, inner - amountW - 28);
    h += Math.max(1, mainLines.length) * 44;
    if (line.note) {
      ctx.font = `500 26px ${FONT}`;
      h += wrapText(ctx, line.note, inner).length * 34;
    }
    h += 10;
  });
  return h;
}

function measureSection(ctx: CanvasRenderingContext2D, lines: FeeLine[], inner: number) {
  return 36 + 48 + 18 + measureLines(ctx, lines, inner) + 24;
}

function drawLines(ctx: CanvasRenderingContext2D, lines: FeeLine[], x: number, y: number, inner: number) {
  if (!lines.length) {
    ctx.fillStyle = PLACEHOLDER;
    ctx.font = `500 30px ${FONT}`;
    ctx.fillText("无", x, y + 36);
    return y + 52;
  }
  let cursor = y;
  lines.forEach((line) => {
    ctx.font = `600 32px ${FONT}`;
    const amountW = ctx.measureText(line.amount).width;
    ctx.font = `500 32px ${FONT}`;
    const mainLines = wrapText(ctx, line.main, inner - amountW - 28);
    const rows = mainLines.length ? mainLines : [line.main];
    rows.forEach((row, i) => {
      cursor += 44;
      ctx.fillStyle = TEXT;
      ctx.font = `500 32px ${FONT}`;
      ctx.fillText(row, x, cursor);
      if (i === 0) {
        ctx.fillStyle = TEAL_DEEP;
        ctx.font = `600 32px ${FONT}`;
        ctx.fillText(line.amount, x + inner - amountW, cursor);
      }
    });
    if (line.note) {
      ctx.font = `500 26px ${FONT}`;
      wrapText(ctx, line.note, inner).forEach((row) => {
        cursor += 34;
        ctx.fillStyle = MUTED;
        ctx.fillText(row, x, cursor);
      });
    }
    cursor += 10;
  });
  return cursor;
}

function drawSection(
  ctx: CanvasRenderingContext2D,
  title: string,
  total: string,
  lines: FeeLine[],
  y: number,
  inner: number
) {
  const h = measureSection(ctx, lines, inner);
  fillRound(ctx, PAD, y, WIDTH - PAD * 2, h, 20, WHITE, BORDER);
  const x = PAD + 28;
  ctx.fillStyle = TEXT;
  ctx.font = `700 34px ${FONT}`;
  ctx.fillText(title, x, y + 52);
  ctx.fillStyle = TEAL_DEEP;
  ctx.font = `700 34px ${FONT}`;
  const tw = ctx.measureText(total).width;
  ctx.fillText(total, x + inner - tw, y + 52);
  ctx.strokeStyle = BORDER;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(x, y + 72);
  ctx.lineTo(x + inner, y + 72);
  ctx.stroke();
  drawLines(ctx, lines, x, y + 78, inner);
  return y + h + 20;
}

export function sheetFilename(bill: Pick<BadmintonBillForm, "playDate" | "title">) {
  const date = bill.playDate || "未填日期";
  const title = (bill.title || "")
    .trim()
    .replace(/[\\/:*?"<>|]/g, "")
    .slice(0, 40);
  return title ? `羽毛球结算-${date}-${title}.png` : `羽毛球结算-${date}.png`;
}

export function renderBillSheet(bill: BadmintonBillForm & { operatorName?: string }) {
  const totals = summarize(bill);
  const courts = courtLines(bill.courtItems);
  const balls = ballLines(bill.ballItems);
  const remark = (bill.remark || "").trim();
  const canvas = document.createElement("canvas");
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("无法绘制结算图");

  const inner = WIDTH - PAD * 2 - 56;
  const courtH = measureSection(ctx, courts, inner);
  const ballH = measureSection(ctx, balls, inner);
  const highlightH = 248;
  ctx.font = `500 30px ${FONT}`;
  const remarkLines = remark ? wrapText(ctx, remark, inner) : [];
  const remarkH = remarkLines.length ? 36 + 40 + remarkLines.length * 42 + 28 : 0;
  const footerH = 56;
  const height =
    PAD + HEADER_H + 20 + courtH + 20 + ballH + 20 + highlightH + (remarkH ? remarkH + 20 : 0) + footerH + PAD;
  canvas.width = WIDTH;
  canvas.height = Math.max(height, 720);

  ctx.fillStyle = PAGE;
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  fillRound(ctx, PAD, PAD, WIDTH - PAD * 2, HEADER_H, 24, TEAL);
  ctx.fillStyle = WHITE;
  ctx.font = `700 48px ${FONT}`;
  ctx.fillText("羽毛球结算", PAD + 36, PAD + 64);
  ctx.font = `500 28px ${FONT}`;
  ctx.fillStyle = TEAL_SUB;
  const sub = [bill.playDate || "未填日期", (bill.title || "").trim() || "未填标题", `${totals.people}人`]
    .filter(Boolean)
    .join("  ·  ");
  ctx.fillText(ellipsis(ctx, sub, inner + 8), PAD + 36, PAD + 114);

  let y = PAD + HEADER_H + 20;
  y = drawSection(ctx, "场地费", formatMoney(totals.courtTotal), courts, y, inner);
  y = drawSection(ctx, "用球费用", formatMoney(totals.ballTotal), balls, y, inner);

  fillRound(ctx, PAD, y, WIDTH - PAD * 2, highlightH, 20, TEAL_SOFT);
  const x = PAD + 28;
  ctx.fillStyle = REGULAR;
  ctx.font = `600 30px ${FONT}`;
  ctx.fillText("总费用", x, y + 52);
  ctx.fillStyle = TEAL_DEEP;
  ctx.font = `700 34px ${FONT}`;
  const grand = formatMoney(totals.grandTotal);
  ctx.fillText(grand, x + inner - ctx.measureText(grand).width, y + 52);

  ctx.fillStyle = TEAL;
  ctx.font = `600 28px ${FONT}`;
  const perLabel = "个人应付";
  const perLabelW = ctx.measureText(perLabel).width;
  ctx.fillText(perLabel, PAD + (WIDTH - PAD * 2 - perLabelW) / 2, y + 118);

  ctx.fillStyle = TEAL_DEEP;
  ctx.font = `800 64px ${FONT}`;
  const perText = `¥ ${formatMoney(totals.perPerson)}`;
  const perW = ctx.measureText(perText).width;
  ctx.fillText(perText, PAD + (WIDTH - PAD * 2 - perW) / 2, y + 186);

  ctx.fillStyle = MUTED;
  ctx.font = `500 26px ${FONT}`;
  const peopleText = `${totals.people} 人均摊`;
  const peopleW = ctx.measureText(peopleText).width;
  ctx.fillText(peopleText, PAD + (WIDTH - PAD * 2 - peopleW) / 2, y + 224);
  y += highlightH + 20;

  if (remarkLines.length) {
    fillRound(ctx, PAD, y, WIDTH - PAD * 2, remarkH, 20, WHITE, BORDER);
    ctx.fillStyle = MUTED;
    ctx.font = `600 28px ${FONT}`;
    ctx.fillText("备注", x, y + 48);
    ctx.fillStyle = TEXT;
    ctx.font = `500 30px ${FONT}`;
    let ry = y + 52;
    remarkLines.forEach((line) => {
      ry += 42;
      ctx.fillText(line, x, ry);
    });
  }

  ctx.fillStyle = PLACEHOLDER;
  ctx.font = `500 24px ${FONT}`;
  const foot = bill.operatorName ? `记录人 ${bill.operatorName}  ·  仅作分摊对账` : "仅作分摊对账";
  ctx.fillText(foot, PAD + 8, canvas.height - 28);
  return canvas;
}

export { canvasToBlob, downloadBlob };
