/**
 * 复制文本到剪贴板。
 * 线上是 http + IP（非安全上下文），navigator.clipboard 不存在，退回 execCommand("copy")。
 * 成功弹 okMessage，失败弹「复制失败，请手动选中复制」，返回是否成功。
 */
export async function copyText(text: string, okMessage = "已复制"): Promise<boolean> {
  const value = String(text ?? "");
  try {
    await navigator.clipboard.writeText(value);
    ElMessage.success(okMessage);
    return true;
  } catch {
    // 没有 clipboard 接口或被拒绝，走下面的旧方式
  }
  const ta = document.createElement("textarea");
  ta.value = value;
  ta.setAttribute("readonly", "readonly");
  ta.style.position = "fixed";
  ta.style.left = "-9999px";
  ta.style.top = "0";
  document.body.appendChild(ta);
  ta.select();
  ta.setSelectionRange(0, value.length);
  let ok = false;
  try {
    ok = document.execCommand("copy");
  } catch {
    ok = false;
  } finally {
    ta.remove();
  }
  if (ok) ElMessage.success(okMessage);
  else ElMessage.error("复制失败，请手动选中复制");
  return ok;
}
