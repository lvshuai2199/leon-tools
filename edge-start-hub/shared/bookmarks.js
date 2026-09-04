const MAIN_FOLDER_PATTERN = /^(favorites bar|bookmarks bar|收藏夹栏|书签栏|主收藏夹)$/i;

export async function getBookmarkTree() {
  const [root] = await chrome.bookmarks.getTree();
  return root;
}

export function findMainBookmarkFolder(root, preferredId = "") {
  const topFolders = (root?.children || []).filter((node) => !node.url);
  if (preferredId) {
    const preferred = findNode(root, preferredId);
    if (preferred && !preferred.url) return preferred;
  }
  return topFolders.find((folder) => MAIN_FOLDER_PATTERN.test(folder.title)) || topFolders[0] || root;
}

export function findNode(node, id) {
  if (!node) return null;
  if (node.id === id) return node;
  for (const child of node.children || []) {
    const found = findNode(child, id);
    if (found) return found;
  }
  return null;
}

export function flattenBookmarks(node, path = [], ancestorIds = []) {
  if (!node) return [];
  if (node.url) {
    return [{
      id: node.id,
      parentId: node.parentId,
      title: node.title || node.url,
      url: node.url,
      path,
      ancestorIds
    }];
  }

  const nextPath = node.title ? [...path, node.title] : path;
  const nextAncestors = node.id === "0" ? ancestorIds : [...ancestorIds, node.id];
  return (node.children || []).flatMap((child) => flattenBookmarks(child, nextPath, nextAncestors));
}

export function listFolders(root) {
  const folders = [];
  function visit(node, depth, parentPath) {
    if (!node || node.url) return;
    const path = node.title ? [...parentPath, node.title] : parentPath;
    if (node.id !== "0") {
      folders.push({ id: node.id, title: node.title || "未命名文件夹", depth, path });
    }
    for (const child of node.children || []) visit(child, depth + (node.id === "0" ? 0 : 1), path);
  }
  visit(root, 0, []);
  return folders;
}
