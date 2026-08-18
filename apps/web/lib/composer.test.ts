import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

/**
 * Static checks on the composer markup. There is no browser test framework in this repository and
 * this PR does not add one, so the parts that cannot be expressed as pure functions — which control
 * renders the provider, and where its choices come from — are verified against the source.
 */
const page = readFileSync(new URL("../app/page.tsx", import.meta.url), "utf8");
const api = readFileSync(new URL("./api.ts", import.meta.url), "utf8");

test("provider choices are loaded from the backend status API", () => {
  assert.match(api, /providers:\s*\(\)\s*=>\s*json<Provider\[\]>\("\/api\/v1\/providers"\)/);
  assert.match(page, /api\.providers\(\)/);
  assert.match(page, /providers\.map\(option =>\s*<option key=\{option\.id\} value=\{option\.id\}>/);
});

test("the free-text provider field is replaced by a select", () => {
  assert.match(page, /<select aria-label="Provider" value=\{provider\}/);
  assert.doesNotMatch(page, /<input[^>]*value=\{provider\}/);
});

test("the model field remains an editable text input", () => {
  assert.match(page, /<input aria-label="Model" value=\{model\}[^>]*onChange=/);
});

test("the browser never holds a provider credential", () => {
  for (const source of [page, api]) {
    assert.doesNotMatch(source, /NEXT_PUBLIC_/);
    assert.doesNotMatch(source, /apiKey|api_key|Authorization/i);
    assert.doesNotMatch(source, /localStorage/);
  }
});
