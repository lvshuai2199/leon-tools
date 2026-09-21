import { parseCrabOrders } from "./crab-parse.js";
import assert from "node:assert/strict";
import test from "node:test";

test("parse tab table with header", () => {
  const text = [
    "序号\t姓名\t电话\t地址\t规格\t数量",
    "1\t王大姐\t13800138000\t广东省阳江市江城区岗列街道幸福路12号\t3.5母\t20只",
    "2\t陈师傅\t13912345678\t广西玉林市福绵区沙田镇东风村\t4公\t15只",
  ].join("\n");
  const rows = parseCrabOrders(text);
  assert.equal(rows.length, 2);
  assert.equal(rows[0].customerName, "王大姐");
  assert.equal(rows[0].phone, "13800138000");
  assert.equal(rows[0].spec, "3.5母");
  assert.equal(rows[0].quantity, 20);
  assert.equal(rows[1].spec, "4公");
});

test("parse spaced lines anchored by phone", () => {
  const text = "张三 18600001111 海南省海口市美兰区蓝天路3号 2.8母 8只\n4 李梅 13700002222 广东省湛江市霞山区人民大道88号 3母 12";
  const rows = parseCrabOrders(text);
  assert.equal(rows.length, 2);
  assert.equal(rows[0].customerName, "张三");
  assert.equal(rows[0].spec, "2.8母");
  assert.equal(rows[1].seqNo, 4);
  assert.equal(rows[1].quantity, 12);
});

test("skip header only", () => {
  assert.equal(parseCrabOrders("序号 姓名 电话 地址 规格 数量").length, 0);
});
