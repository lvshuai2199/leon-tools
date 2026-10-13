"use strict";

function isRemoteHead(name) {
  const text = String(name || "");
  const slash = text.indexOf("/");
  if (slash <= 0) return false;
  return text.slice(slash + 1) === "HEAD";
}

function parseTrack(upstream, track) {
  if (!upstream) return { ahead: null, behind: null };
  const text = String(track || "");
  const ahead = /ahead\s+(\d+)/i.exec(text);
  const behind = /behind\s+(\d+)/i.exec(text);
  return {
    ahead: ahead ? Number(ahead[1]) : 0,
    behind: behind ? Number(behind[1]) : 0,
  };
}

function sortNodes(nodes) {
  nodes.sort((a, b) => {
    const byName = a.name.localeCompare(b.name);
    if (byName) return byName;
    if (a.type === b.type) return 0;
    return a.type === "leaf" ? -1 : 1;
  });
  for (const node of nodes) {
    if (node.children) sortNodes(node.children);
  }
}

function insertLeaf(nodes, segments, leaf) {
  let level = nodes;
  for (let i = 0; i < segments.length; i++) {
    const part = segments[i];
    if (i === segments.length - 1) {
      level.push({ ...leaf, type: "leaf", name: part });
      return;
    }
    let folder = level.find((node) => node.type === "folder" && node.name === part);
    if (!folder) {
      folder = { type: "folder", name: part, children: [] };
      level.push(folder);
    }
    level = folder.children;
  }
}

function nest(items) {
  const nodes = [];
  for (const item of items) {
    const segments = String(item.segments || "")
      .split("/")
      .filter(Boolean);
    if (!segments.length) continue;
    insertLeaf(nodes, segments, item.leaf);
  }
  sortNodes(nodes);
  return nodes;
}

function countsOf(branch, remote) {
  if (remote) return { ahead: null, behind: null };
  return {
    ahead: branch && branch.ahead != null ? branch.ahead : null,
    behind: branch && branch.behind != null ? branch.behind : null,
  };
}

function branchTree(branches, options = {}) {
  const list = Array.isArray(branches) ? branches : [];
  const detached = Boolean(options.detached);
  const marked = list.find((branch) => branch && !branch.remote && branch.current);
  const headName = detached ? "" : options.current || marked?.name || "";
  const headSource = list.find((branch) => branch && !branch.remote && branch.name === headName) || marked;

  const headLeaf = !headName
    ? {
        type: "leaf",
        name: "游离 HEAD",
        checkout: "",
        current: true,
        remote: false,
        detached: true,
        kind: "head",
        ahead: null,
        behind: null,
      }
    : {
        type: "leaf",
        name: headName,
        checkout: headName,
        current: true,
        remote: false,
        detached: false,
        kind: "local",
        ...countsOf(headSource, false),
      };

  const local = nest(
    list
      .filter((branch) => branch && !branch.remote && branch.name)
      .map((branch) => ({
        segments: branch.name,
        leaf: {
          checkout: branch.name,
          current: Boolean(branch.current) || branch.name === headName,
          remote: false,
          kind: "local",
          ...countsOf(branch, false),
        },
      }))
  );

  const remoteItems = new Map();
  for (const branch of list) {
    if (!branch || !branch.remote || !branch.name || isRemoteHead(branch.name)) continue;
    const slash = branch.name.indexOf("/");
    if (slash <= 0) continue;
    const remoteName = branch.name.slice(0, slash);
    const rest = branch.name.slice(slash + 1);
    if (!rest || rest === "HEAD") continue;
    if (!remoteItems.has(remoteName)) remoteItems.set(remoteName, []);
    remoteItems.get(remoteName).push({
      segments: rest,
      leaf: {
        checkout: branch.name,
        current: false,
        remote: true,
        kind: "remote",
        ahead: null,
        behind: null,
      },
    });
  }
  const remoteNodes = [...remoteItems.keys()]
    .sort((a, b) => a.localeCompare(b))
    .map((name) => ({ type: "folder", name, remote: true, children: nest(remoteItems.get(name)) }));

  return {
    groups: [
      { id: "head", label: "HEAD", nodes: [headLeaf] },
      { id: "local", label: "本地", nodes: local },
      { id: "remote", label: "远程", nodes: remoteNodes },
    ],
  };
}

function decorateRefs(raw, remotes) {
  const names = [...new Set((remotes || []).filter(Boolean))].sort((a, b) => b.length - a.length);
  const out = [];
  for (const part of String(raw || "").split(",")) {
    const item = part.trim();
    if (!item || item === "HEAD") continue;
    if (item.startsWith("HEAD -> ")) {
      const name = item.slice("HEAD -> ".length).trim();
      if (name) out.push({ name, kind: "local" });
      continue;
    }
    if (item.startsWith("tag: ")) {
      const name = item.slice("tag: ".length).trim();
      if (name) out.push({ name, kind: "tag" });
      continue;
    }
    const remote = names.find((candidate) => item.startsWith(`${candidate}/`));
    if (remote && item === `${remote}/HEAD`) continue;
    if (remote) out.push({ name: item, kind: "remote" });
    else out.push({ name: item, kind: "local" });
  }
  return out;
}

module.exports = { branchTree, decorateRefs, parseTrack, isRemoteHead };
