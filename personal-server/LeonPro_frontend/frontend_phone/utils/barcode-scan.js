const FORMATS = ["code_128", "code_39", "code_93", "ean_13", "ean_8", "codabar", "itf", "upc_a", "upc_e", "qr_code"];

export function normalizeTrackingNo(raw) {
  return String(raw || "")
    .replace(/\s+/g, "")
    .replace(/[^0-9A-Za-z]/g, "")
    .toUpperCase();
}

export function extractTrackingNo(text) {
  const upper = String(text || "").toUpperCase().replace(/[—–＿\-]/g, "");
  const labeled = upper.match(/(?:运单|快递|物流|发货)?(?:单号|编号)[^\n0-9A-Z]{0,8}([A-Z]{0,4}\d{10,18}[A-Z]{0,4})/);
  if (labeled) {
    const value = normalizeTrackingNo(labeled[1]);
    if (isTrackingLike(value)) return value;
  }
  const tokens = upper.match(/[A-Z]*\d[A-Z0-9]{8,21}/g) || [];
  const ranked = tokens
    .map((token) => normalizeTrackingNo(token))
    .filter(isTrackingLike)
    .map((token) => ({ token, score: scoreTracking(token) }))
    .sort((a, b) => b.score - a.score);
  return ranked[0] ? ranked[0].token : "";
}

function isTrackingLike(value) {
  if (!value || value.length < 10 || value.length > 22) return false;
  if (/^1[3-9]\d{9}$/.test(value)) return false;
  if (/^\d{15,18}$/.test(value) && /^(11|12|13|21|31|32|33|34|35|36|37|41|42|43|44|50|51|52|53|61)/.test(value)) {
    return false;
  }
  return /\d{8,}/.test(value);
}

function scoreTracking(value) {
  let score = value.length;
  if (/^(SF|JD|YT|ZT|YD|HT|TT|STO|EMS|CN|ZTO|YTO|YD)/.test(value)) score += 24;
  if (/^\d{12,15}$/.test(value)) score += 12;
  if (/[A-Z]/.test(value) && /\d/.test(value)) score += 8;
  return score;
}

export function pickTrackingNoFromImage() {
  return new Promise((resolve, reject) => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "image/*";
    input.onchange = async () => {
      const file = input.files && input.files[0];
      if (!file) {
        resolve("");
        return;
      }
      try {
        resolve(await recognizeTrackingFromFile(file));
      } catch (error) {
        reject(error);
      }
    };
    input.click();
  });
}

export function scanTrackingNo() {
  return new Promise((resolve, reject) => {
    const root = document.createElement("div");
    root.className = "barcode-scan-root";
    root.innerHTML = `
      <div class="barcode-scan-mask">
        <video class="barcode-scan-video" playsinline muted autoplay></video>
        <div class="barcode-scan-frame"></div>
        <div class="barcode-scan-hint">对准条码，或选快递单照片</div>
        <div class="barcode-scan-actions">
          <label class="barcode-scan-btn ghost">图片识别
            <input class="barcode-scan-file" type="file" accept="image/*" hidden />
          </label>
          <button class="barcode-scan-btn" type="button" data-close>关闭</button>
        </div>
      </div>
    `;
    const style = document.createElement("style");
    style.textContent = SCAN_CSS;
    root.appendChild(style);
    document.body.appendChild(root);

    const video = root.querySelector(".barcode-scan-video");
    const fileInput = root.querySelector(".barcode-scan-file");
    const closeBtn = root.querySelector("[data-close]");
    let stream = null;
    let timer = 0;
    let done = false;

    const finish = (value, err) => {
      if (done) return;
      done = true;
      window.clearInterval(timer);
      if (stream) stream.getTracks().forEach((track) => track.stop());
      root.remove();
      if (err) reject(err);
      else resolve(value);
    };

    closeBtn.addEventListener("click", () => finish(null, Object.assign(new Error("cancel"), { name: "AbortError" })));
    fileInput.addEventListener("change", async () => {
      const file = fileInput.files && fileInput.files[0];
      if (!file) return;
      const hint = root.querySelector(".barcode-scan-hint");
      if (hint) hint.textContent = "正在识别图片...";
      try {
        const code = await recognizeTrackingFromFile(file);
        if (code) finish(code);
        else window.alert("没有识别到快递单号，请换张更清晰的面单照片");
      } catch (error) {
        console.error(error);
        window.alert("识别失败，请重试");
      } finally {
        if (hint && !done) hint.textContent = "对准条码，或选快递单照片";
      }
    });

    startCamera(video)
      .then((media) => {
        stream = media;
        timer = window.setInterval(async () => {
          try {
            const code = await detectFromVideo(video);
            if (code) finish(code);
          } catch {
            /* keep scanning */
          }
        }, 280);
      })
      .catch(() => {
        /* camera denied: user can still pick a photo */
      });
  });
}

export async function recognizeTrackingFromFile(file) {
  const barcode = await detectFromFile(file);
  if (barcode) return barcode;
  const { recognizePhoto } = await import("./crab-ocr.js");
  const text = await recognizePhoto(file);
  return extractTrackingNo(text);
}

async function startCamera(video) {
  const stream = await navigator.mediaDevices.getUserMedia({
    audio: false,
    video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } },
  });
  video.srcObject = stream;
  await video.play();
  return stream;
}

async function detectFromVideo(video) {
  if (!video.videoWidth) return "";
  const detector = createDetector();
  if (detector) {
    const codes = await detector.detect(video);
    return pickCode(codes);
  }
  return "";
}

async function detectFromFile(file) {
  const bitmap = await createImageBitmap(file);
  try {
    const detector = createDetector();
    if (!detector) return "";
    const direct = pickCode(await detector.detect(bitmap));
    if (direct) return direct;
    const canvas = document.createElement("canvas");
    const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height));
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    canvas.getContext("2d").drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    return pickCode(await detector.detect(canvas));
  } finally {
    bitmap.close?.();
  }
}

function createDetector() {
  if (typeof window === "undefined" || typeof window.BarcodeDetector !== "function") return null;
  try {
    return new window.BarcodeDetector({ formats: FORMATS });
  } catch {
    return new window.BarcodeDetector();
  }
}

function pickCode(codes) {
  if (!Array.isArray(codes) || !codes.length) return "";
  const ranked = codes
    .map((item) => normalizeTrackingNo(item.rawValue || item.raw || ""))
    .filter(isTrackingLike)
    .map((token) => ({ token, score: scoreTracking(token) }))
    .sort((a, b) => b.score - a.score);
  if (ranked[0]) return ranked[0].token;
  const raw = normalizeTrackingNo(codes[0].rawValue || codes[0].raw || "");
  return raw.length >= 8 ? raw : "";
}

const SCAN_CSS = `
.barcode-scan-root{position:fixed;inset:0;z-index:4000;}
.barcode-scan-mask{position:absolute;inset:0;background:#000;display:flex;flex-direction:column;align-items:center;justify-content:flex-end;}
.barcode-scan-video{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;}
.barcode-scan-frame{position:absolute;left:10%;right:10%;top:28%;height:22%;border:2px solid #60a5fa;border-radius:12px;box-shadow:0 0 0 9999px rgba(0,0,0,.35);}
.barcode-scan-hint{position:relative;z-index:1;margin-bottom:12px;color:#fff;font-size:14px;}
.barcode-scan-actions{position:relative;z-index:1;display:flex;gap:10px;padding:0 16px 28px;width:100%;box-sizing:border-box;}
.barcode-scan-btn{flex:1;height:44px;border:none;border-radius:10px;background:#2563eb;color:#fff;font-size:15px;display:flex;align-items:center;justify-content:center;}
.barcode-scan-btn.ghost{background:#1f2937;}
`;
