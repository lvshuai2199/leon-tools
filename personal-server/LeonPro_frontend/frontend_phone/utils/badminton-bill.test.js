import assert from "node:assert/strict";
import test from "node:test";
import { money, summarize } from "./badminton-bill.js";

test("court and ball split evenly", () => {
  const totals = summarize({
    participantCount: 4,
    courtItems: [{ courtCount: 2, hours: 2, unitPrice: 40 }],
    ballItems: [{ brand: "亚狮龙7号", quantity: 3, unitPrice: 70 }],
  });
  assert.equal(totals.courtTotal, 160);
  assert.equal(totals.ballTotal, 210);
  assert.equal(totals.grandTotal, 370);
  assert.equal(totals.perPerson, 92.5);
});

test("round per person half up", () => {
  assert.equal(money(260 / 3), 86.67);
});

test("missing people defaults to one", () => {
  const totals = summarize({ participantCount: 0, courtItems: [], ballItems: [] });
  assert.equal(totals.people, 1);
  assert.equal(totals.perPerson, 0);
});
