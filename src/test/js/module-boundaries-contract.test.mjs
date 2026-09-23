import test from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

/**
 * ARC-005, DB-014–DB-021, OPS-019, TST-003: the D32 schema rules must all have
 * owners. The analyzer previously omitted eight rules and retained two retired
 * verdicts. Every discovered rule must be assigned once; unresolved/stale counts
 * must be zero, while all twelve independently recorded findings stay visible.
 */
test("module boundary analysis assigns the complete catalogue without unresolved or stale verdicts", () => {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), "../../..");
  const result = spawnSync(process.execPath, ["scripts/module-boundaries.cjs"], {
    cwd: root, encoding: "utf8", timeout: 30000,
  });
  assert.equal(result.error, undefined, result.error?.message);
  assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
  const counts = result.stdout.match(/found (\d+), assigned (\d+), problems (\d+)/);
  assert.ok(counts, "no ownership totals reported");
  assert.ok(Number(counts[1]) > 0, "empty catalogue is not a successful analysis");
  assert.equal(counts[1], counts[2], "some discovered rules have no owner");
  assert.equal(counts[3], "0");
  assert.match(result.stdout, /same or higher layer without a verdict: 0\b/);
  assert.doesNotMatch(result.stdout, /verdicts that match no mention/);
  assert.match(result.stdout, /reviewer findings reproduced: 12 of 12/);
});
