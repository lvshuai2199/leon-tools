export function showToast(title) {
  const existing = document.getElementById("leonpro-toast");
  if (existing) existing.remove();
  const el = document.createElement("div");
  el.id = "leonpro-toast";
  el.textContent = title;
  Object.assign(el.style, {
    position: "fixed",
    left: "50%",
    bottom: "88px",
    transform: "translateX(-50%)",
    maxWidth: "80vw",
    padding: "10px 16px",
    borderRadius: "10px",
    background: "rgba(17, 24, 39, 0.92)",
    color: "#fff",
    fontSize: "14px",
    zIndex: "9999",
    pointerEvents: "none",
  });
  document.body.appendChild(el);
  setTimeout(() => el.remove(), 1800);
}

export async function copyText(text) {
  await navigator.clipboard.writeText(String(text));
  showToast("已复制");
}

export function confirmAction(title, content) {
  return window.confirm(`${title}\n${content}`);
}
