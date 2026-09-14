import test from "node:test";
import assert from "node:assert/strict";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../../..");
const specsDir = resolve(root, ".sdd/specs");
const read = (path) => readFileSync(path, "utf8");

const SECTIONS = [
  "## 1. Context & Goal",
  "## 2. Actors & Roles",
  "## 3. Functional Requirements",
  "## 4. Non-functional Requirements",
  "## 5. Data",
  "## 6. Error Handling",
  "## 7. Acceptance Criteria",
  "## 8. Out of Scope",
  "## Notes / Open Questions",
];

const RULE_ROW = /^\| ([A-Z]+-\d{3}) \|/gm;
const SCENARIO_ROW = /^\| (AC-[A-Z]+-\d{3}) \| ([^|]*)\|/gm;

function featureDirs() {
  return readdirSync(specsDir)
    .filter((name) => name.startsWith("feature-"))
    .map((name) => join(specsDir, name));
}

function specs() {
  return featureDirs().map((dir) => ({ dir, text: read(join(dir, "SPEC.md")) }));
}

/** Expands `TSK-020–TSK-022` and `PRJ-003-PRJ-005` into every identifier in the range. */
function expandReferences(text) {
  const ids = [];
  for (const match of text.matchAll(/([A-Z]+)-(\d{3})(?:\s*[–-]\s*(?:\1-)?(\d{3}))?/g)) {
    const first = Number(match[2]);
    const last = match[3] ? Number(match[3]) : first;
    for (let n = first; n <= last; n += 1) {
      ids.push(`${match[1]}-${String(n).padStart(3, "0")}`);
    }
  }
  return ids;
}

function markdownFiles(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return markdownFiles(path);
    return name.endsWith(".md") ? [path] : [];
  });
}

/**
 * Protects GOV-016: every requirement has exactly one canonical location, a
 * feature's rules live in that feature's spec, and its acceptance scenarios live
 * in section 7 of the same spec.
 *
 * Observable break: the specification has been lost and rebuilt twice, and was
 * split into eight specs on 14 September 2026. A rule pasted into a second spec,
 * a scenario that still names a rule after it was renumbered, a spec whose
 * header version no longer matches its changelog, or a link left pointing at a
 * file that moved would each pass review unnoticed. Every one of those was found
 * by hand at least once during that restructuring.
 *
 * Expected values are derived from GOV-016 and the spec layout recorded in
 * .sdd/requirements.md: eight numbered sections plus notes, in that order; one
 * row per identifier across all specs; the newest changelog heading equal to
 * the version in the spec header.
 */
test("every feature spec has its SPEC.md and CHANGELOG.md with the eight sections in order", () => {
  const dirs = featureDirs();
  assert.ok(dirs.length > 0, "no feature spec found under .sdd/specs");
  for (const dir of dirs) {
    const name = relative(root, dir);
    assert.ok(existsSync(join(dir, "SPEC.md")), `${name} has no SPEC.md`);
    assert.ok(existsSync(join(dir, "CHANGELOG.md")), `${name} has no CHANGELOG.md`);
    const text = read(join(dir, "SPEC.md"));
    let position = -1;
    for (const heading of SECTIONS) {
      const at = text.indexOf(`\n${heading}\n`);
      assert.ok(at > position, `${name}/SPEC.md lacks "${heading}" or has it out of order`);
      position = at;
    }
  }
});

test("each rule identifier has exactly one row across all specs", () => {
  const seen = new Map();
  for (const { dir, text } of specs()) {
    for (const match of text.matchAll(RULE_ROW)) {
      const where = relative(root, dir);
      assert.ok(!seen.has(match[1]), `${match[1]} is defined in both ${seen.get(match[1])} and ${where}`);
      seen.set(match[1], where);
    }
  }
  assert.ok(seen.size > 0, "no rule rows found");
});

test("each acceptance scenario is unique and names only rules that exist", () => {
  const all = specs();
  const rules = new Set(all.flatMap(({ text }) => [...text.matchAll(RULE_ROW)].map((m) => m[1])));
  const scenarios = new Set();
  for (const { dir, text } of all) {
    for (const match of text.matchAll(SCENARIO_ROW)) {
      assert.ok(!scenarios.has(match[1]), `${match[1]} appears more than once`);
      scenarios.add(match[1]);
      for (const id of expandReferences(match[2])) {
        assert.ok(rules.has(id), `${match[1]} in ${relative(root, dir)} names ${id}, which no spec defines`);
      }
    }
  }
});

test("every rule identifier mentioned in a spec is defined by some spec", () => {
  const all = specs();
  const rules = new Set(all.flatMap(({ text }) => [...text.matchAll(RULE_ROW)].map((m) => m[1])));
  const prefixes = new Set([...rules].map((id) => id.split("-")[0]));
  for (const { dir, text } of all) {
    for (const match of text.matchAll(/(?<![A-Z-])([A-Z]{2,4})-(\d{3})(?!\d)/g)) {
      if (!prefixes.has(match[1])) continue;
      const id = `${match[1]}-${match[2]}`;
      assert.ok(rules.has(id), `${relative(root, dir)}/SPEC.md mentions ${id}, which no spec defines`);
    }
  }
});

test("each spec header version matches the newest entry in its changelog", () => {
  for (const dir of featureDirs()) {
    const name = relative(root, dir);
    const header = read(join(dir, "SPEC.md")).match(/\*\*Version:\*\* (\d+\.\d+\.\d+)/);
    const newest = read(join(dir, "CHANGELOG.md")).match(/^## (\d+\.\d+\.\d+) /m);
    assert.ok(header, `${name}/SPEC.md has no version line`);
    assert.ok(newest, `${name}/CHANGELOG.md has no version entry`);
    assert.equal(header[1], newest[1], `${name}: SPEC.md says ${header[1]} but CHANGELOG.md starts at ${newest[1]}`);
  }
});

test("relative links in the documentation resolve", () => {
  const files = [
    ...readdirSync(root).filter((name) => name.endsWith(".md")).map((name) => join(root, name)),
    ...markdownFiles(resolve(root, ".sdd")),
    ...markdownFiles(resolve(root, "docs")),
  ];
  for (const file of files) {
    for (const match of read(file).matchAll(/\]\(([^)#\s]+)(?:#[^)]*)?\)/g)) {
      const target = match[1];
      if (/^(https?:|mailto:)/.test(target)) continue;
      assert.ok(existsSync(resolve(dirname(file), target)), `${relative(root, file)} links to ${target}, which does not exist`);
    }
  }
});
