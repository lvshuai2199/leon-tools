"use strict";

const crypto = require("crypto");
const fs = require("fs");

const EXTS = new Set([".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg", ".ico"]);
const MAX_BYTES = 50 * 1024 * 1024;

function pngSize(buf) {
  if (buf.length < 24) return null;
  if (buf.subarray(0, 8).toString("hex") !== "89504e470d0a1a0a") return null;
  if (buf.subarray(12, 16).toString("ascii") !== "IHDR") return null;
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
}

function gifSize(buf) {
  if (buf.length < 10) return null;
  const tag = buf.subarray(0, 6).toString("ascii");
  if (tag !== "GIF87a" && tag !== "GIF89a") return null;
  return { width: buf.readUInt16LE(6), height: buf.readUInt16LE(8) };
}

function jpegSize(buf) {
  if (buf.length < 4 || buf[0] !== 0xff || buf[1] !== 0xd8) return null;
  let offset = 2;
  while (offset + 8 < buf.length) {
    if (buf[offset] !== 0xff) {
      offset += 1;
      continue;
    }
    const marker = buf[offset + 1];
    if (marker === 0xd8 || marker === 0x01) {
      offset += 2;
      continue;
    }
    if (marker === 0xd9 || marker === 0xda) break;
    const length = buf.readUInt16BE(offset + 2);
    if (length < 2 || offset + 2 + length > buf.length) break;
    if (marker >= 0xc0 && marker <= 0xc3) {
      return { height: buf.readUInt16BE(offset + 5), width: buf.readUInt16BE(offset + 7) };
    }
    offset += 2 + length;
  }
  return null;
}

function webpSize(buf) {
  if (buf.length < 16) return null;
  if (buf.subarray(0, 4).toString("ascii") !== "RIFF") return null;
  if (buf.subarray(8, 12).toString("ascii") !== "WEBP") return null;
  if (buf.subarray(12, 16).toString("ascii") !== "VP8X" || buf.length < 30) return { width: 0, height: 0 };
  return {
    width: 1 + (buf[24] | (buf[25] << 8) | (buf[26] << 16)),
    height: 1 + (buf[27] | (buf[28] << 8) | (buf[29] << 16)),
  };
}

function imageSize(buf) {
  return pngSize(buf) || jpegSize(buf) || gifSize(buf) || webpSize(buf) || { width: 0, height: 0 };
}

function inspectBuffer(buf) {
  const dim = imageSize(buf);
  return {
    sha256: crypto.createHash("sha256").update(buf).digest("hex"),
    size: buf.length,
    width: dim.width,
    height: dim.height,
  };
}

function inspectFile(abs) {
  return inspectBuffer(fs.readFileSync(abs));
}

module.exports = { EXTS, MAX_BYTES, imageSize, inspectBuffer, inspectFile };
