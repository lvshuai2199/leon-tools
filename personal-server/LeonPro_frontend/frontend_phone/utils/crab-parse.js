const PHONE = /(?<!\d)(1[3-9]\d{9})(?!\d)/;
const PHONE_SPACED = /(?<!\d)(1[3-9]\d)[\s-]*(\d{4})[\s-]*(\d{4})(?!\d)/g;
const SPEC_A = /(\d+(?:\.\d+)?)\s*两?\s*([公母])(?:蟹)?/;
const SPEC_B = /([公母])(?:蟹)?\s*(\d+(?:\.\d+)?)\s*两?/;
const QTY_ZHI = /(\d+)\s*只/;
const QTY_MARK = /(?:数量|只数|共|合计)[:：]?\s*(\d+)/;
const QTY_TAIL = /(?:[xX×*]\s*)(\d+)\s*$/;
const LEAD_SEQ = /^(\d{1,4})[\\.、\s]+/;
const CN_BEFORE_ZHI = /([一二三四五六七八九十两]+)只/g;
const CN_BEFORE_LIANG = /([一二三四五六七八九十]+)(?=两\s*[公母])/g;
const LABELS = /(?:收件人|收货人|客户|姓名|电话|手机号|手机|地址|住址|规格|数量|只数)\s*[:：]\s*/g;

export function parseCrabOrders(raw) {
  if (raw == null) return [];
  const text = preprocess(raw);
  if (!text) return [];
  if (looksLikeTable(text)) return parseLines(text);
  const blocks = splitBlocks(text);
  if (blocks.length === 1) return parseLines(text);
  const rows = [];
  for (const block of blocks) {
    const row = parseLoose(block.replace(/\n/g, " "));
    if (row && hasContent(row)) rows.push(row);
  }
  return rows.length ? rows : parseLines(text);
}

function preprocess(raw) {
  let text = String(raw).replace(/\r\n/g, "\n").replace(/\r/g, "\n").replace(/\u00a0/g, " ").replace(/\u3000/g, " ");
  text = text.replace(PHONE_SPACED, "$1$2$3");
  text = text.replace(LABELS, " ");
  text = text.replace(CN_BEFORE_ZHI, (_, n) => `${chineseToInt(n)}只`);
  text = text.replace(CN_BEFORE_LIANG, (_, n) => String(chineseToInt(n)));
  return text.replace(/两只/g, "2只").trim();
}

function parseLines(text) {
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

function splitBlocks(text) {
  const parts = text.split(/\n\s*\n+/).map((p) => p.trim()).filter(Boolean);
  return parts.length ? parts : [text];
}

function looksLikeTable(text) {
  const tabs = (text.match(/\t/g) || []).length;
  return tabs >= 3 || looksLikeHeader(text.split("\n", 2)[0] || "");
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
  let text = normalizeLine(line);
  let seqNo = null;
  const seq = text.match(LEAD_SEQ);
  if (seq) {
    seqNo = parseIntSafe(seq[1]);
    text = text.slice(seq[0].length).trim();
  } else {
    const parts = text.split(/[\s\t]+/, 2);
    if (parts[0] && /^\d{1,4}$/.test(parts[0]) && parts[1]) {
      seqNo = parseIntSafe(parts[0]);
      text = parts[1].trim();
    }
  }
  const extracted = extractSpecQty(text);
  const remainder = extracted.address || "";
  const match = remainder.match(PHONE);
  if (!match) {
    const fallback = parseWithoutPhone(normalizeLine(line));
    if (!fallback) return null;
    if (seqNo != null && fallback.seqNo == null) fallback.seqNo = seqNo;
    if (!fallback.spec) fallback.spec = extracted.spec;
    if (fallback.quantity == null) fallback.quantity = extracted.quantity;
    return fallback;
  }
  const phone = match[1];
  const left = remainder.slice(0, match.index).trim();
  const right = remainder.slice(match.index + phone.length).trim();
  const pair = splitNameAddress(left, right);
  return {
    seqNo,
    phone,
    customerName: cleanName(pair.name),
    address: trimToNull(pair.address),
    spec: extracted.spec,
    quantity: extracted.quantity,
  };
}

const ADDR_TOKEN = /[省市区县旗盟州镇乡村街道路巷弄号楼栋单元室园苑屯寨庄组社场厦座层]/;

function splitNameAddress(left, right) {
  const a = String(left || "").trim();
  const b = String(right || "").trim();
  if (a && b) {
    const la = visualLen(a);
    const lb = visualLen(b);
    if (la < lb) return { name: a, address: b };
    if (lb < la) return { name: b, address: a };
    if (looksLikeName(a) && !looksLikeName(b)) return { name: a, address: b };
    if (looksLikeName(b) && !looksLikeName(a)) return { name: b, address: a };
    if (looksLikeAddress(b) && !looksLikeAddress(a)) return { name: a, address: b };
    if (looksLikeAddress(a) && !looksLikeAddress(b)) return { name: b, address: a };
    return { name: a, address: b };
  }
  if (!a && b) return splitNameFromBlob(b);
  if (a) return splitNameFromBlob(a);
  return { name: null, address: null };
}

function splitNameFromBlob(blob) {
  const text = String(blob || "").trim();
  if (!text) return { name: null, address: null };
  const parts = text.split(/\s+/);
  if (parts.length >= 2) {
    let best = -1;
    let bestLen = Infinity;
    parts.forEach((part, i) => {
      const n = visualLen(part);
      if (n > 0 && n <= 5 && looksLikeName(part) && n < bestLen) {
        best = i;
        bestLen = n;
      }
    });
    if (best < 0) {
      parts.forEach((part, i) => {
        const n = visualLen(part);
        if (n > 0 && n <= 5 && n < bestLen) {
          best = i;
          bestLen = n;
        }
      });
    }
    if (best >= 0) {
      return {
        name: parts[best],
        address: parts.filter((_, i) => i !== best).join(" "),
      };
    }
  }
  if (text.length <= 5 && looksLikeName(text)) return { name: text, address: null };
  let bestStart = -1e6;
  let bestEnd = -1e6;
  let startLen = 2;
  let endLen = 2;
  const max = Math.min(4, Math.max(0, text.length - 2));
  for (let len = 2; len <= max; len += 1) {
    const prefix = text.slice(0, len);
    const restAfter = text.slice(len);
    let startScore = scoreName(prefix) + scoreAddress(restAfter);
    if (/^[\u4e00-\u9fff]{2}(省|市|区|县|镇|乡)/.test(restAfter)) startScore += 8;
    else if (/^(省|市|区)/.test(restAfter) || /^[\u4e00-\u9fff]{3}(省|市)/.test(restAfter)) startScore += 3;
    if (startScore > bestStart) {
      bestStart = startScore;
      startLen = len;
    }
    const suffix = text.slice(text.length - len);
    const restBefore = text.slice(0, text.length - len);
    let endScore = scoreName(suffix) + scoreAddress(restBefore);
    if (addressLikelyAtHead(text)) endScore += 6;
    if (endScore > bestEnd) {
      bestEnd = endScore;
      endLen = len;
    }
  }
  if (addressLikelyAtHead(text) || bestEnd >= bestStart) {
    return { name: text.slice(text.length - endLen), address: text.slice(0, text.length - endLen) };
  }
  return { name: text.slice(0, startLen), address: text.slice(startLen) };
}

function addressLikelyAtHead(text) {
  return /^[\u4e00-\u9fff]{0,3}(省|市|自治区)/.test(text)
    || /^(北京|上海|天津|重庆|广西|西藏|宁夏|新疆|内蒙)/.test(text);
}

function looksLikeName(raw) {
  const value = String(raw || "").replace(/\s+/g, "");
  if (value.length < 1 || value.length > 5) return false;
  if (ADDR_TOKEN.test(value)) return false;
  if (/\d/.test(value)) return false;
  return /^[\u4e00-\u9fff·•]{1,5}$/.test(value);
}

function looksLikeAddress(raw) {
  return ADDR_TOKEN.test(String(raw || ""));
}

function scoreName(raw) {
  if (looksLikeName(raw)) return 10 + Math.max(0, 4 - visualLen(raw));
  if (ADDR_TOKEN.test(String(raw || ""))) return -12;
  if (/\d/.test(String(raw || ""))) return -8;
  return -3;
}

function scoreAddress(raw) {
  const text = String(raw || "");
  if (!text) return -4;
  let score = 0;
  if (text.includes("省")) score += 3;
  if (text.includes("市")) score += 3;
  if (text.includes("区") || text.includes("县")) score += 2;
  if (text.includes("路") || text.includes("街") || text.includes("道")) score += 2;
  if (text.includes("号") || text.includes("村") || text.includes("镇")) score += 2;
  if (text.length >= 8) score += 2;
  if (text.length >= 16) score += 2;
  return score;
}

function visualLen(raw) {
  return String(raw || "").replace(/\s+/g, "").length;
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
  const extracted = extractSpecQty(after);
  row.spec = extracted.spec;
  row.quantity = extracted.quantity;
  row.address = extracted.address;
}

function extractSpecQty(raw) {
  const text = String(raw || "").trim();
  let spec = null;
  let specStart = -1;
  let specEnd = -1;
  const specA = text.match(SPEC_A);
  const specB = text.match(SPEC_B);
  if (specA) {
    spec = specA[1] + specA[2];
    specStart = specA.index;
    specEnd = specA.index + specA[0].length;
  } else if (specB) {
    spec = specB[2] + specB[1];
    specStart = specB.index;
    specEnd = specB.index + specB[0].length;
  }
  let quantity = null;
  let qtyStart = -1;
  let qtyEnd = -1;
  const qtyZhi = text.match(QTY_ZHI);
  const qtyMark = text.match(QTY_MARK);
  const qtyTail = text.match(QTY_TAIL);
  if (qtyZhi) {
    quantity = parseIntSafe(qtyZhi[1]);
    qtyStart = qtyZhi.index;
    qtyEnd = qtyZhi.index + qtyZhi[0].length;
  } else if (qtyMark) {
    quantity = parseIntSafe(qtyMark[1]);
    qtyStart = qtyMark.index;
    qtyEnd = qtyMark.index + qtyMark[0].length;
  } else if (qtyTail) {
    quantity = parseIntSafe(qtyTail[1]);
    qtyStart = qtyTail.index;
    qtyEnd = qtyTail.index + qtyTail[0].length;
  } else {
    const bare = text.match(/(\d+)\s*$/);
    if (bare && specEnd >= 0 && bare.index >= specEnd) {
      quantity = parseIntSafe(bare[1]);
      qtyStart = bare.index;
      qtyEnd = bare.index + bare[0].length;
    }
  }
  return { spec, quantity, address: trimToNull(removeSpans(text, specStart, specEnd, qtyStart, qtyEnd)) };
}

function removeSpans(text, s1, e1, s2, e2) {
  const a = s1 >= 0;
  const b = s2 >= 0;
  if (!a && !b) return text;
  const from = a && b ? Math.min(s1, s2) : a ? s1 : s2;
  const to = a && b ? Math.max(e1, e2) : a ? e1 : e2;
  return `${text.slice(0, from)} ${text.slice(to)}`.replace(/ {2,}/g, " ").trim();
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
  if (h.includes("规格") || h.includes("尺码") || h.includes("公母") || h.includes("两")) return "spec";
  if (h.includes("数量") || h.includes("只数") || h.includes("只") || h.includes("qty")) return "qty";
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
  const extracted = extractSpecQty(value);
  if (extracted.spec) return extracted.spec;
  return value.replace(/ /g, "").replace(/两/g, "");
}

function parseQuantity(raw) {
  if (raw == null) return null;
  const extracted = extractSpecQty(raw);
  if (extracted.quantity != null) return extracted.quantity;
  const zhi = String(raw).match(QTY_ZHI);
  if (zhi) return parseIntSafe(zhi[1]);
  const match = String(raw).match(/(\d+)/);
  return match ? parseIntSafe(match[1]) : null;
}

function chineseToInt(raw) {
  if (!raw) return 0;
  if (raw === "两") return 2;
  const map = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10, 两: 2 };
  let total = 0;
  let num = 0;
  for (const c of raw) {
    const v = map[c];
    if (v == null) continue;
    if (c === "十") {
      total += (num === 0 ? 1 : num) * 10;
      num = 0;
    } else {
      num = v;
    }
  }
  return total + num;
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
