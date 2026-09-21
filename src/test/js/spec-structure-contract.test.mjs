import test from "node:test";
import assert from "node:assert/strict";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { markdownAnchors, markdownLinks } from "../../../scripts/markdown-contract.mjs";

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

/** D38 folded each removed feature-* changelog into its module under this heading. */
const RETAINED_HISTORY = "## Retained history";
const RULE_ROW = /^\| ([A-Z]+-\d{3}) \|/gm;
const SCENARIO_ROW = /^\| (AC-[A-Z]+-\d{3}) \| ([^|]*)\|/gm;

/** GOV-016: checks a labelled rule/scenario link against its canonical file. */
function assertCanonicalTraceTarget(id, target, source, owners) {
  assert.ok(owners.has(id), `${relative(root, source)}: unknown trace ${id}`);
  const path = decodeURIComponent(target.split("#", 1)[0]);
  const destination = path ? resolve(dirname(source), path) : source;
  assert.equal(destination, owners.get(id),
    `${relative(root, source)}: ${id} link ${target} does not point to its canonical owner`);
}

/**
 * GOV-016, TST-003: ACC-027 belongs to Authentication, not platform. The review
 * changed its link to another existing file and all document checks still passed.
 * These hand-assigned owners require both a rule and scenario to reject that
 * wrong file, while allowing a correct relative path with a section fragment.
 */
test("trace ownership rejects an existing wrong file for both rules and scenarios", () => {
  const source = resolve(specsDir, "identity/features/authentication/SPEC.md");
  const owners = new Map([["ACC-001", source], ["AC-ACC-001", source]]);
  for (const id of owners.keys()) {
    assert.doesNotThrow(() => assertCanonicalTraceTarget(id, "SPEC.md#logout", source, owners));
    assert.doesNotThrow(() => assertCanonicalTraceTarget(id, "#logout", source, owners));
    assert.throws(() => assertCanonicalTraceTarget(id, "../../../platform/MODULE.md", source, owners),
      /does not point to its canonical owner/);
  }
  assert.throws(() => assertCanonicalTraceTarget("ACC-999", "SPEC.md", source, owners), /unknown trace/);
});

/**
 * Protects GOV-016 and the D34 document hierarchy. A move must not make shared
 * rules invisible to the catalogue, and no module may keep a second home: D38
 * removed the parallel feature-* tree after folding its history into the module.
 * The eight modules are the seven ARC-005 business modules and shared platform.
 */
test("module contracts and nested features are all discoverable without legacy duplicates", () => {
  const modules = ["identity", "internship", "calendar", "attendance", "project", "notification", "reporting", "platform"];
  const discovered = new Set(specs().map(({ dir }) => relative(specsDir, dir).replaceAll("\\", "/")));
  for (const name of modules) {
    const moduleDir = join(specsDir, name);
    assert.ok(existsSync(join(moduleDir, "MODULE.md")), `${name} has no shared module contract`);
    assert.ok(discovered.has(name), `${name}/MODULE.md is invisible to rule checks`);
    const children = readdirSync(join(moduleDir, "features"));
    assert.ok(children.length > 0, `${name} has no features`);
    for (const child of children) {
      assert.ok(discovered.has(`${name}/features/${child}`), `${name}/${child} is invisible to rule checks`);
      const content = read(join(moduleDir, "features", child, "SPEC.md"));
      assert.ok(content.includes("](../../MODULE.md)"), `${name}/${child} has no inheritance link`);
    }
    assert.ok(!existsSync(join(specsDir, `feature-${name}`)),
      `feature-${name} is a second home for ${name}; D38 removed that parallel tree`);
    assert.ok(read(join(moduleDir, "CHANGELOG.md")).includes(RETAINED_HISTORY),
      `${name}/CHANGELOG.md lost the history retained from its removed index`);
  }
});

/**
 * D38: the Status field states whether a document's own content is settled, so it may not
 * disagree with the open questions. A document that still names one must carry the
 * inherited-baseline status; a document that names none must not. The wording each status
 * means is defined once, in the specification map.
 */
test("each document's Status agrees with the open questions it names", () => {
  const OPEN = /remains? (?:unresolved|open|an open)|still (?:records|needs|unresolved)|Decision required|must not be invented|not yet settled|remains? a separate gap/i;
  const statuses = new Set(["DRAFT", "INHERITED BASELINE; SEE OPEN QUESTIONS", "APPROVED BUSINESS BASELINE"]);
  assert.ok(read(join(specsDir, "README.md")).includes("## What the Status field means"),
    "the specification map no longer defines what the Status field means");
  for (const { dir, filename, text } of specs()) {
    const name = `${relative(specsDir, dir).replaceAll("\\", "/")}/${filename}`;
    const status = text.match(/\*\*Status:\*\* ([^·]+?) ·/);
    assert.ok(status, `${name} has no Status field`);
    assert.ok(statuses.has(status[1]), `${name} uses the undefined Status "${status[1]}"`);
    const notes = text.slice(text.indexOf("## Notes / Open Questions"));
    const namesOpen = text.includes("## Notes / Open Questions") && OPEN.test(notes);
    const saysOpen = status[1] === "INHERITED BASELINE; SEE OPEN QUESTIONS";
    assert.equal(saysOpen, namesOpen, namesOpen
      ? `${name} names an open question but its Status does not say so`
      : `${name} names no open question, so its Status must not point at one`);
  }
});

/** Finds shared contracts; compatibility indexes are deliberately not contracts. */
function moduleDirs() {
  return readdirSync(specsDir)
    .map((name) => join(specsDir, name))
    .filter((dir) => existsSync(join(dir, "MODULE.md")));
}

/** Finds every feature inside each module, so a new folder cannot escape checks. */
function featureDirs() {
  return moduleDirs().flatMap((dir) => readdirSync(join(dir, "features"))
    .map((name) => join(dir, "features", name)));
}

/** Includes both levels of canonical rules and scenarios in every catalogue check. */
function specs() {
  return [
    ...moduleDirs().map((dir) => ({ dir, filename: "MODULE.md" })),
    ...featureDirs().map((dir) => ({ dir, filename: "SPEC.md" })),
  ].map(({ dir, filename }) => ({ dir, filename, text: read(join(dir, filename)) }));
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
 * feature-owned rules live in that feature's spec, shared rules in MODULE.md,
 * and scenarios in section 7 of the contract that owns the exercised behavior.
 *
 * Observable break: the specification has been lost and rebuilt twice, and was
 * split into eight specs on 14 September 2026. A rule pasted into a second spec,
 * a scenario that still names a rule after it was renumbered, a spec whose
 * header version no longer matches its changelog, or a link left pointing at a
 * file that moved would each pass review unnoticed. Every one of those was found
 * by hand at least once during that restructuring.
 *
 * Expected values are derived from GOV-016 and the spec layout recorded in
 * .sdd/specs/_template.md: eight numbered sections plus notes, in that order; one
 * row per identifier across all specs; the newest changelog heading equal to
 * the version in the spec header.
 */
test("every module and feature contract has its changelog and eight sections in order", () => {
  const documents = specs();
  assert.ok(featureDirs().length > 0, "no feature spec found under .sdd/specs");
  for (const { dir, filename, text } of documents) {
    const name = relative(root, dir);
    assert.ok(existsSync(join(dir, filename)), `${name} has no ${filename}`);
    assert.ok(existsSync(join(dir, "CHANGELOG.md")), `${name} has no CHANGELOG.md`);
    let position = -1;
    for (const heading of SECTIONS) {
      const at = text.indexOf(`\n${heading}\n`);
      assert.ok(at > position, `${name}/${filename} lacks "${heading}" or has it out of order`);
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

/**
 * GOV-016, TST-007: every declared operation needs an explicit trace or gap. The
 * expected row names come from section 3, independently of the section 7 map;
 * a removed operation row or a stale scenario link must fail this check.
 */
test("every feature operation maps to existing rules and scenarios or names its gap", () => {
  const all = specs();
  const rules = new Set(all.flatMap(({ text }) => [...text.matchAll(RULE_ROW)].map((m) => m[1])));
  const scenarios = new Set(all.flatMap(({ text }) => [...text.matchAll(SCENARIO_ROW)].map((m) => m[1])));
  for (const dir of featureDirs()) {
    const text = read(join(dir, "SPEC.md"));
    const name = relative(root, dir);
    const operations = [...text.slice(text.indexOf(SECTIONS[2]), text.indexOf("### Canonical feature rules"))
      .matchAll(/^### (.+)$/gm)].map((m) => m[1]);
    const start = text.indexOf("| Operation | Actor and observable outcome |");
    assert.ok(start >= 0, `${name}: missing operation map`);
    const rows = text.slice(start).split("\n").slice(2);
    const mapped = [];
    for (const row of rows) {
      if (!row.startsWith("| ")) break;
      const cells = row.split("|").slice(1, -1).map((cell) => cell.trim());
      assert.equal(cells.length, 5, `${name}: malformed operation row`);
      const operation = cells[0].match(/^\[([^\]]+)\]\(#[^)]+\)$/);
      assert.ok(operation, `${name}: operation must link to its heading`);
      mapped.push(operation[1]);
      assert.ok(cells[1] && cells[4], `${name}: actor/outcome or boundary is empty`);
      for (const [column, catalogue] of [[2, rules], [3, scenarios]]) {
        const ids = [...cells[column].matchAll(/\[((?:AC-)?[A-Z]+-\d{3})\]/g)].map((m) => m[1]);
        assert.ok(ids.length || cells[column] === "None specified", `${name}: missing explicit trace/gap`);
        for (const id of ids) assert.ok(catalogue.has(id), `${name}: unknown trace ${id}`);
        if (!ids.length) assert.match(cells[4], /decision|no dedicated|missing|specify/i, `${name}: absent trace has no explanation`);
      }
    }
    assert.deepEqual(mapped, operations, `${name}: operation headings and acceptance map differ`);
    assert.ok(text.includes("### Required contracts") && text.includes("### Related workflows and joint checks"),
      `${name}: contract dependencies and related work must be distinguished`);
  }
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

/**
 * GOV-016, TST-007: labels in operation maps and required contracts must lead to
 * the document defining that ID. The owner is derived from canonical rows, not
 * the link under test, so another existing file cannot satisfy the assertion.
 */
test("rule and scenario links in canonical contracts point to their defining document", () => {
  const documents = specs();
  const owners = new Map(documents.flatMap(({ dir, filename, text }) =>
    [...text.matchAll(/^\| ((?:AC-)?[A-Z]+-\d{3}) \|/gm)]
      .map((match) => [match[1], resolve(dir, filename)])));
  for (const { dir, filename, text } of documents) {
    for (const match of text.matchAll(/\[((?:AC-)?[A-Z]+-\d{3})\]\(([^)]+)\)/g)) {
      assertCanonicalTraceTarget(match[1], match[2], resolve(dir, filename), owners);
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
  for (const { dir, filename, text } of specs()) {
    const name = relative(root, dir);
    const header = text.match(/\*\*Version:\*\* (\d+\.\d+\.\d+)/);
    const newest = read(join(dir, "CHANGELOG.md")).match(/^## (\d+\.\d+\.\d+) /m);
    assert.ok(header, `${name}/${filename} has no version line`);
    assert.ok(newest, `${name}/CHANGELOG.md has no version entry`);
    assert.equal(header[1], newest[1], `${name}: ${filename} says ${header[1]} but CHANGELOG.md starts at ${newest[1]}`);
  }
});

/** GOV-016: a document link must resolve to both its file and its named section. */
test("relative links and section fragments in the documentation resolve", () => {
  const files = [
    ...readdirSync(root).filter((name) => name.endsWith(".md")).map((name) => join(root, name)),
    ...markdownFiles(resolve(root, ".sdd")),
    ...markdownFiles(resolve(root, "docs")),
  ];
  for (const file of files) {
    for (const target of markdownLinks(read(file))) {
      if (/^[a-z][a-z\d+.-]*:/i.test(target)) continue;
      const hash = target.indexOf("#");
      const path = decodeURIComponent(hash < 0 ? target : target.slice(0, hash));
      const destination = path ? resolve(dirname(file), path) : file;
      assert.ok(existsSync(destination), `${relative(root, file)} links to ${target}, which does not exist`);
      if (hash >= 0 && target.slice(hash + 1) && destination.endsWith(".md")) {
        const fragment = decodeURIComponent(target.slice(hash + 1));
        assert.ok(markdownAnchors(read(destination)).has(fragment),
          `${relative(root, file)} links to ${target}, whose section does not exist`);
      }
    }
  }
});

/**
 * The three checks below guard what the six above do not: meaning rather than
 * shape. Every check above passes on a specification whose prose contradicts its
 * own rules, and that is the failure this project keeps meeting. Three instances
 * were found by hand on 16 September 2026 alone: a use case specified in full
 * with no actor line, a count written in words that its own table disproved, and
 * a decision superseded twice with no pointer where a reader stands.
 *
 * Each check is written so that a wrong document fails and a growing document
 * does not. None of them asks whether a rule is right; they ask whether two
 * statements about the same fact agree.
 */

const NUMBER_WORDS = {
  ten: 10, eleven: 11, twelve: 12, thirteen: 13, fourteen: 14, fifteen: 15,
  sixteen: 16, seventeen: 17, eighteen: 18, nineteen: 19, twenty: 20,
  "twenty-one": 21, "twenty-two": 22, "twenty-three": 23, "twenty-four": 24,
};

/** Rules, scenarios, and which rules a scenario covers, counted the way §22 counts them. */
function catalogue() {
  const rules = new Set();
  const covered = new Set();
  let scenarios = 0;
  for (const { text } of specs()) {
    for (const match of text.matchAll(RULE_ROW)) rules.add(match[1]);
    for (const match of text.matchAll(SCENARIO_ROW)) {
      scenarios += 1;
      for (const id of expandReferences(match[2])) covered.add(id);
    }
  }
  const uncovered = [...rules].filter((id) => !covered.has(id)).sort();
  return { rules, scenarios, uncovered };
}

test("every use case a spec defines is named in its Actors & Roles section", () => {
  for (const { dir, text } of specs()) {
    const name = relative(root, dir);
    const from = text.indexOf(SECTIONS[1]);
    const to = text.indexOf(SECTIONS[2]);
    assert.ok(from >= 0 && to > from, `${name}: sections 2 and 3 are not both present`);
    const named = new Set([...text.slice(from, to).matchAll(/UC-\d{2}/g)].map((match) => match[0]));
    for (const match of text.matchAll(/^#{3,4} (UC-\d{2}) —/gm)) {
      assert.ok(
        named.has(match[1]),
        `${name}: section 3 specifies ${match[1]} but section 2 names no actor for it. A reader meets section 2 first, and a use case with no declared actor is how an authorization gap enters unseen.`,
      );
    }
  }
});

test("every count the specification states equals what it counts", () => {
  const { rules, scenarios, uncovered } = catalogue();
  const platform = read(join(specsDir, "platform/MODULE.md"));

  /** Every row of any table in the spec whose measure is `label`, read as a number. */
  const stateAll = (label, expected) => {
    const rows = platform.split("\n").filter((line) => line.startsWith(`| ${label} | `));
    assert.ok(rows.length, `the platform spec has no "${label}" row`);
    for (const row of rows) {
      const value = Number(row.split("|")[2].trim().split(" ")[0]);
      assert.equal(value, expected, `the platform spec says ${label} is ${value}; it is ${expected}`);
    }
  };
  stateAll("Normative rules", rules.size);
  stateAll("Acceptance scenarios", scenarios);
  stateAll("Normative rules, sections 1–22", rules.size);
  stateAll("Rules with a §20 acceptance scenario", rules.size - uncovered.length);
  stateAll("Rules declared without one, with reason", uncovered.length);

  const word = platform.match(/\*\*Rules with no system-level acceptance criterion\.\*\* (\w+(?:-\w+)?) requirements/);
  assert.ok(word, "the §20 exclusion note does not open with a number");
  const spelled = NUMBER_WORDS[word[1].toLowerCase()];
  assert.ok(spelled !== undefined, `the §20 exclusion note says "${word[1]}", which is not a number this test knows`);
  assert.equal(
    spelled,
    uncovered.length,
    `the §20 exclusion note says ${word[1]} requirements have no scenario; ${uncovered.length} do. A number written in words does not move when the list beneath it grows.`,
  );

  const table = platform.slice(platform.indexOf("| Group | IDs | Why no scenario |"));
  const listed = new Set();
  for (const row of table.split("\n").slice(2)) {
    if (!row.startsWith("| ")) break;
    for (const id of expandReferences(row.split("|")[2].replaceAll("`", ""))) listed.add(id);
  }
  assert.deepEqual(
    [...listed].sort(),
    uncovered,
    "the §20 exclusion table and the rules that actually have no acceptance scenario are not the same set",
  );
});

test("every decision is both listed and written, and none is referenced that does not exist", () => {
  const decisions = resolve(root, ".sdd/decisions.md");
  const text = read(decisions);
  const written = [...text.matchAll(/^## (D\d+)\. /gm)].map((match) => match[1]);
  const listed = [...text.matchAll(/^\| (D\d+) \|/gm)].map((match) => match[1]);
  const byNumber = (a, b) => Number(a.slice(1)) - Number(b.slice(1));
  assert.deepEqual(
    [...listed].sort(byNumber),
    [...written].sort(byNumber),
    "the index at the top of .sdd/decisions.md and the decisions written below it are not the same set",
  );

  const exists = new Set(written);
  const files = [
    ...readdirSync(root).filter((name) => name.endsWith(".md")).map((name) => join(root, name)),
    ...markdownFiles(resolve(root, ".sdd")),
    ...markdownFiles(resolve(root, "docs")),
  ];
  for (const file of files) {
    read(file).split("\n").forEach((line, index) => {
      for (const match of line.matchAll(/`(D\d+)`/g)) {
        assert.ok(
          exists.has(match[1]),
          `${relative(root, file)}:${index + 1} cites ${match[1]}, which .sdd/decisions.md does not record`,
        );
      }
    });
  }
});

/**
 * A state transition table summarizes rules and adds nothing to them. Observable break: a
 * table names a status no rule defines, such as a typo or a state the rules never allowed,
 * and a reader implements the table instead of the rules. Expected values come from the rule
 * rows themselves: every status in a table's From or To column must appear, in backticks, in
 * some rule of some spec, alone or inside a transition such as `DRAFT → ACTIVE`. `(none)` and `(deleted)` mark creation and deletion.
 */
test("every status a state transition table names is a status some rule names", () => {
  const all = specs();
  const named = new Set();
  for (const { text } of all) {
    for (const line of text.split("\n")) {
      if (!/^\| [A-Z]+-\d{3} \|/.test(line)) continue;
      for (const span of line.matchAll(/`([^`]+)`/g)) {
        for (const token of span[1].matchAll(/\b[A-Z][A-Z_]{2,}\b/g)) named.add(token[0]);
      }
    }
  }
  let tables = 0;
  for (const { dir, text } of all) {
    const lines = text.split("\n");
    lines.forEach((line, index) => {
      if (line !== "| From | Action | To | Who | Rules |") return;
      tables += 1;
      for (let row = index + 2; row < lines.length && lines[row].startsWith("| "); row += 1) {
        const cells = lines[row].split("|").map((cell) => cell.trim());
        for (const cell of [cells[1], cells[3]]) {
          for (const state of cell.split(",").map((part) => part.trim())) {
            if (state === "(none)" || state === "(deleted)") continue;
            const bare = state.replace(/^`|`$/g, "");
            assert.ok(
              /^`[A-Z][A-Z_]+`$/.test(state) && named.has(bare),
              `${relative(root, dir)}/SPEC.md: a state transition table names ${state}, which no rule names`,
            );
          }
        }
      }
    });
  }
  assert.ok(tables > 0, "no state transition table found");
});
