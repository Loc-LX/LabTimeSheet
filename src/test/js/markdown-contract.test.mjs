import test from "node:test";
import assert from "node:assert/strict";
import { markdownAnchors, markdownLinks } from "../../../scripts/markdown-contract.mjs";

/**
 * GOV-016, TST-003: a valid file can have a broken section link. Expected anchors
 * come from visible headings and explicit IDs; a code example is not a section.
 */
test("Markdown anchors retain explicit IDs, Unicode and duplicate-heading suffixes", () => {
  const document = '# Identity\n## 2. Actors & Roles\n## Lịch sử\n## History-1\n## History\n## History\n<a id="attendance-a3"></a>\n```md\n## Example only\n```\n';
  assert.deepEqual([...markdownAnchors(document)].sort(), [
    "identity", "2-actors--roles", "lịch-sử", "history-1", "history", "history-2", "attendance-a3",
  ].sort());
  assert.equal(markdownAnchors(document).has("example-only"), false);
  assert.equal(markdownAnchors(document).has("review-probe-nonexistent-section"), false);
});

/** GOV-016: pure fragment links need validation; illustrative code links do not. */
test("Markdown destinations include same-page and spaced paths but exclude code examples", () => {
  const document = '[local](#history) [file](MODULE.md#history) [space](<My notes.md#history>)\n`[code](missing.md)`\n~~~md\n[example](absent.md)\n~~~\n';
  assert.deepEqual(markdownLinks(document), ["#history", "MODULE.md#history", "My notes.md#history"]);
});
