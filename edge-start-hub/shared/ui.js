import { faviconUrl } from "./browser-data.js";

export function createFavicon(url, size = 32) {
  const image = document.createElement("img");
  image.className = "favicon";
  image.src = faviconUrl(url, size);
  image.alt = "";
  image.width = size;
  image.height = size;
  image.addEventListener("error", () => {
    image.classList.add("favicon--fallback");
    image.removeAttribute("src");
  });
  return image;
}

export function getHostLabel(url) {
  try {
    return new URL(url).host;
  } catch {
    return url;
  }
}

export function debounce(callback, delay = 160) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => callback(...args), delay);
  };
}

export function makeId() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
