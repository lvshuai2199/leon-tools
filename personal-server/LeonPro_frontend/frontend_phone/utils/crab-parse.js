const PHONE = /(?<!\d)(1[3-9]\d{9})(?!\d)/;
const SPEC_QTY = /(\d+(?:\.\d+)?)\s*([公母])\s*(?:蟹)?\s*(\d+)\s*只?\s*$/;
const QTY_ONLY = /(\d+)\s*只\s*$/;
const SPEC_ONLY = /(\d+(?:\.\d+)?)\s*([公母])\s*(?:蟹)?\s*$/;
const LEAD_SEQ = /^(\d{1,4})[\\.、\s]+/;

export function parseCrabOrders(raw) {
  if (raw == null) return [];
  const text = String(raw).replace(/\r\n/g, "\n").replace(/\r/g, "\n").replace(/\u00a0/g, " ").trim();
  if (!text) return [];

  const rows = [];
  let header = null;
  for (const original of text.split("\n")) {
    const line = normalizeLine(original);
    if (!line) continue;
    if (!header && looksLikeHeader(line)) {
      header = columnMap(line);
      continue;
    }
    const row = header ? parseByHeader(line, header) : parseLoose(line);
    if (row && hasContent(row)) rows.push(row);
  }
  return rows;
}

export function normalizeLine(original) {
  if (original == null) return "";
  return String(original)
    .replace(/\u3000/g, " ")
    .replace(/\|/g, "\t")
    .trim()
    .replace(/ {2,}/g, " ");
}

export function looksLikeHeader(line) {
  const compact = line.replace(/\t/g, "").replace(/ /g, "");
  return compact.includes("姓名") && (compact.includes("电话") || compact.includes("手机")
    || compact.includes("地址") || compact.includes("规格"));
}

function parseByHeader(line, header) {
  const cols = splitColumns(line);
  if (cols.length === 1) return parseLoose(line);
  const row = {
    seqNo: parseIntSafe(header.get(cols, "seq")),
    customerName: cleanName(header.get(cols, "name")),
    phone: cleanPhone(header.get(cols, "phone")),
    address: trimToNull(header.get(cols, "address")),
    spec: cleanSpec(header.get(cols, "spec")),
    quantity: parseQuantity(header.get(cols, "qty")),
  };
  if (!hasContent(row)) return parseLoose(line);
  fillMissingFromLoose(row, line);
  return row;
}

function parseLoose(line) {
  const match = String(line).match(PHONE);
  if (!match) return parseWithoutPhone(line);
  const phone = match[1];
  const index = match.index;
  const before = line.slice(0, index).trim();
  const after = line.slice(index + phone.length).trim();
  const row = { phone };
  applySeqAndName(row, before);
  applyAddressSpecQty(row, after);
  return row;
}

function parseWithoutPhone(line) {
  const cols = splitColumns(line);
  if (cols.length >= 4) {
    const row = {};
    let i = 0;
    const seq = parseIntSafe(cols[0]);
    if (seq != null) {
      row.seqNo = seq;
      i = 1;
    }
    if (i < cols.length) row.customerName = cleanName(cols[i++]);
    if (i < cols.length && PHONE.test(cols[i])) {
      PHONE.lastIndex = 0;
      row.phone = cleanPhone(cols[i++]);
    }
    if (i < cols.length) row.address = trimToNull(cols[i++]);
    if (i < cols.length) row.spec = cleanSpec(cols[i++]);
    if (i < cols.length) row.quantity = parseQuantity(cols[i]);
    return hasContent(row) ? row : null;
  }
  const row = {};
  applySeqAndName(row, line);
  applyAddressSpecQty(row, "");
  return hasContent(row) ? row : null;
}

function applySeqAndName(row, before) {
  let text = (before || "").trim();
  const seq = text.match(LEAD_SEQ);
  if (seq) {
    row.seqNo = parseIntSafe(seq[1]);
    text = text.slice(seq[0].length).trim();
  } else {
    const parts = text.split(/[\s\t]+/, 2);
    if (parts[0] && /^\d{1,4}$/.test(parts[0])) {
      row.seqNo = parseIntSafe(parts[0]);
      text = parts[1] ? parts[1].trim() : "";
    }
  }
  row.customerName = cleanName(text);
}

function applyAddressSpecQty(row, after) {
  let text = (after || "").trim();
  const specQty = text.match(SPEC_QTY);
  if (specQty) {
    row.spec = specQty[1] + specQty[2];
    row.quantity = parseIntSafe(specQty[3]);
    row.address = trimToNull(text.slice(0, specQty.index));
    return;
  }
  const qty = text.match(QTY_ONLY);
  if (qty) {
    row.quantity = parseIntSafe(qty[1]);
    text = text.slice(0, qty.index).trim();
  }
  const spec = text.match(SPEC_ONLY);
  if (spec) {
    row.spec = spec[1] + spec[2];
    text = text.slice(0, spec.index).trim();
  }
  row.address = trimToNull(text);
}

function fillMissingFromLoose(row, line) {
  const loose = parseLoose(line);
  if (!loose) return;
  if (!row.customerName) row.customerName = loose.customerName;
  if (!row.phone) row.phone = loose.phone;
  if (!row.address) row.address = loose.address;
  if (!row.spec) row.spec = loose.spec;
  if (row.quantity == null) row.quantity = loose.quantity;
  if (row.seqNo == null) row.seqNo = loose.seqNo;
}

function splitColumns(line) {
  if (line.includes("\t")) return line.split("\t");
  if (line.includes(",") && line.split(",").length >= 4) return line.split(",");
  return [line];
}

function columnMap(headerLine) {
  const cols = headerLine.includes("\t") ? headerLine.split("\t") : headerLine.split(/[\s,]+/);
  const index = {};
  cols.forEach((col, i) => {
    const key = classify(col);
    if (key && index[key] == null) index[key] = i;
  });
  return {
    get(values, key) {
      const i = index[key];
      if (i == null || i < 0 || i >= values.length) return null;
      return values[i];
    },
  };
}

function classify(header) {
  const h = String(header || "").replace(/ /g, "").toLowerCase();
  if (h.includes("序号") || h === "no" || h === "#") return "seq";
  if (h.includes("姓名") || h.includes("客户") || h.includes("收件")) return "name";
  if (h.includes("电话") || h.includes("手机") || h.includes("phone")) return "phone";
  if (h.includes("地址") || h.includes("住址")) return "address";
  if (h.includes("规格") || h.includes("尺码") || h.includes("公母")) return "spec";
  if (h.includes("数量") || h.includes("只数") || h.includes("qty")) return "qty";
  return null;
}

function cleanName(raw) {
  const value = trimToNull(raw);
  if (!value) return null;
  return value.replace(/^[\d.、\s]+/, "").trim() || null;
}

function cleanPhone(raw) {
  if (raw == null) return null;
  const match = String(raw).replace(/ /g, "").match(PHONE);
  return match ? match[1] : trimToNull(raw);
}

function cleanSpec(raw) {
  const value = trimToNull(raw);
  if (!value) return null;
  const match = value.match(/(\d+(?:\.\d+)?)\s*([公母])/);
  if (match) return match[1] + match[2];
  return value.replace(/ /g, "");
}

function parseQuantity(raw) {
  if (raw == null) return null;
  const match = String(raw).match(/(\d+)/);
  return match ? parseIntSafe(match[1]) : null;
}

function parseIntSafe(raw) {
  if (raw == null) return null;
  const digits = String(raw).replace(/[^0-9]/g, "");
  if (!digits) return null;
  const n = Number(digits);
  return Number.isFinite(n) ? n : null;
}

function hasContent(row) {
  return !!(row && (row.customerName || row.phone));
}

function trimToNull(value) {
  if (value == null) return null;
  const t = String(value).trim();
  return t ? t : null;
}

export function todayStr() {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function shiftDay(dateStr, delta) {
  const [y, m, d] = String(dateStr).split("-").map(Number);
  const dt = new Date(y, m - 1, d);
  dt.setDate(dt.getDate() + delta);
  const yy = dt.getFullYear();
  const mm = String(dt.getMonth() + 1).padStart(2, "0");
  const dd = String(dt.getDate()).padStart(2, "0");
  return `${yy}-${mm}-${dd}`;
}

export function shareUrl(publicId) {
  if (!publicId) return "";
  const path = `/h5/#/pages/crab/share?id=${encodeURIComponent(publicId)}`;
  if (typeof window === "undefined") return path;
  return `${window.location.origin}${path}`;
}
