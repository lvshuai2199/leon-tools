"use strict";

function layout(commits) {
  const list = Array.isArray(commits) ? commits : [];
  const pending = new Set(list.map((commit) => commit && commit.sha).filter(Boolean));
  const rows = [];
  let columns = [];
  const colorBySha = new Map();
  let nextColor = 0;

  function colorOf(sha, fallback) {
    if (!colorBySha.has(sha)) colorBySha.set(sha, fallback == null ? nextColor++ : fallback);
    return colorBySha.get(sha);
  }

  for (const commit of list) {
    const sha = commit.sha;
    const parents = Array.isArray(commit.parents) ? commit.parents.filter(Boolean) : [];
    let lane = columns.indexOf(sha);
    if (lane < 0) {
      lane = columns.length;
      columns = columns.concat([sha]);
    }
    const color = colorOf(sha);
    pending.delete(sha);

    const placed = [];
    let insertFirstAt = 0;
    for (let col = 0; col < columns.length; col++) {
      if (columns[col] === sha) continue;
      if (col < lane) insertFirstAt += 1;
      placed.push({ sha: columns[col], from: col });
    }

    const links = [];
    const nextShas = placed.map((item) => item.sha);
    const fromOfPass = placed.map((item) => item.from);
    const alive = (parent) => pending.has(parent);
    let cursor = insertFirstAt - 1;

    parents.forEach((parent, index) => {
      if (!alive(parent)) {
        links.push({ from: lane, to: lane, color, kind: "bottom" });
        return;
      }
      const existing = nextShas.indexOf(parent);
      if (existing !== -1) {
        links.push({ from: lane, to: existing, color: colorBySha.get(parent) ?? color, kind: "merge" });
        if (index === 0) cursor = existing;
        return;
      }
      const inherited = index === 0 ? color : nextColor++;
      if (!colorBySha.has(parent)) colorBySha.set(parent, inherited);
      const at = index === 0 ? Math.min(Math.max(insertFirstAt, 0), nextShas.length) : Math.min(cursor + 1, nextShas.length);
      nextShas.splice(at, 0, parent);
      fromOfPass.splice(at, 0, null);
      if (index !== 0) {
        for (const link of links) {
          if (link.kind !== "bottom" && link.to >= at) link.to += 1;
        }
      }
      links.push({
        from: lane,
        to: at,
        color: colorBySha.get(parent),
        kind: index === 0 ? "straight" : "fork",
      });
      cursor = at;
    });

    nextShas.forEach((id, to) => {
      const from = fromOfPass[to];
      if (from == null) return;
      links.push({
        from,
        to,
        color: colorBySha.has(id) ? colorBySha.get(id) : colorOf(id),
        kind: "straight",
      });
    });

    rows.push({ sha, lane, color, links });
    columns = nextShas;
  }
  return rows;
}

module.exports = { layout };
