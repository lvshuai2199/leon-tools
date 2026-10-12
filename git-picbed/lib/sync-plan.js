"use strict";

function buildPlan(locals, images, { imageDir, preserveShas }) {
  const keep = preserveShas || new Set();
  const existing = images || [];
  const bySha = new Map(existing.map((img) => [img.sha256, img]));
  const groups = new Map();
  const skipped = [];

  for (const local of locals) {
    if (local.tooBig) {
      skipped.push(local);
      continue;
    }
    if (!groups.has(local.sha256)) groups.set(local.sha256, []);
    groups.get(local.sha256).push(local);
  }

  const nameOwners = new Map();
  for (const img of existing) nameOwners.set(img.name, img.sha256);
  for (const [sha, sources] of groups) {
    if (!nameOwners.has(sources[0].name)) nameOwners.set(sources[0].name, sha);
  }

  const uploads = [];
  const reuses = [];
  const nextImages = [];
  for (const [sha, sources] of groups) {
    const main = sources[0];
    const prev = bySha.get(sha);
    const nameClash = nameOwners.get(main.name) !== sha;
    if (prev) {
      reuses.push({ sha256: sha, sources, image: prev, nameClash });
      nextImages.push({
        sha256: prev.sha256,
        size: main.size,
        width: main.width,
        height: main.height,
        ext: prev.ext,
        name: main.name,
        path: prev.path,
      });
      continue;
    }
    const remotePath = `${imageDir}/${sha.slice(0, 2)}/${sha}${main.ext}`;
    uploads.push({ sha256: sha, sources, local: main, remotePath, nameClash });
    nextImages.push({
      sha256: sha,
      size: main.size,
      width: main.width,
      height: main.height,
      ext: main.ext,
      name: main.name,
      path: remotePath,
    });
  }

  for (const sha of keep) {
    if (groups.has(sha)) continue;
    const prev = bySha.get(sha);
    if (prev) nextImages.push({ ...prev });
  }

  const deletes = existing.filter((img) => !groups.has(img.sha256) && !keep.has(img.sha256));
  nextImages.sort((a, b) => a.sha256.localeCompare(b.sha256));
  return { uploads, reuses, deletes, skipped, nextImages };
}

module.exports = { buildPlan };
