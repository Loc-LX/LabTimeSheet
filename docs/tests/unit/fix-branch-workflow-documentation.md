# Test Evidence: durable fix-branch documentation workflow

- **Test type:** Unit (documentation contract)
- **Requirement IDs:** `OPS-019`, `TST-009`, `TST-010`
- **Scenario IDs:** `AC-TST-001`
- **Test class/method:** `scripts/verify-fix-branch-workflow.cjs --self-test`
- **Implementation commit:** `f013ad7707b36959ddca891fe0d52f81bba3ee80`
- **Round-1 review-fix commits:** `d617769499362e92d058684501af3c1ae6b145b0`, `719e02ea902bfb2dbeddc04f12be3617be3427b5`
- **Round-2 all-guide regression commit:** `97e991317d55db4f7414678a89a45921802a14b8`
- **Round-2 coordination-authority commit:** `9802d5d17f5c07511e1f9cf59ace4b7e48fcdc0e`
- **Round-2 plan-contract commit:** `f98e7f39ef38c7882106ffb250155d2a72dcf0dd`

## Protected behavior

Targeted repairs use the realizable `work/fix/<feature>/<what-fix>` branch and
clean worktree from taskmaster-verified `main`. Contributor guidance must reject
the impossible `work/<feature>/fix/<what-fix>` form while persistent
`work/<feature>` refs exist. The tracked copies of root coordination authority
must use the same rule.

## Test method

Use the tracked Node validator rather than an artificial Java test. It requires
the exact approved statement in each of the six guides. Its self-test creates a
fresh positive nested-branch mutation for every guide and asserts the
file-specific rejection each time. The copied root coordination files are
compared byte-for-byte with their main-root sources and checked for their exact
approved rules. The original RED proves the required branch name was absent
from the four contributor guides; the first review-fix RED proves the
executable regression was absent.

## Hand-derived expected result

The required fix-branch spelling appears exactly once in each of the six tracked
documentation artifacts, and the only nested-form reference is inside that
artifact's exact approved statement. Each independent simulated positive
nested-branch recommendation must fail. The three tracked coordination files
must exactly match the authorized main-root versions. The existing SRS generator
must still report 260 requirements and 14 use cases.

## RED

**Command**

```text
rg -n -F 'work/fix/<feature>/<what-fix>' AGENTS.md README.md DEVELOPMENT.md TESTING.md
```

**Observed result**

```text
exit 1; no matching lines
```

The failure was expected: the required realizable repair-branch rule was absent
before this documentation change.

### Review-fix RED

**Command**

```text
node scripts/verify-fix-branch-workflow.cjs --self-test
```

**Observed result**

```text
exit 1
Error: Cannot find module '.../scripts/verify-fix-branch-workflow.cjs'
```

The executable regression required to reject a positive nested-branch
recommendation did not exist.

### Round-2 RED

**Command**

```text
node scripts/verify-fix-branch-workflow.cjs --self-test | rg -x 'Positive nested branch recommendations: 6/6 rejected'
```

**Observed result**

```text
exit 1; no matching line
```

The prior self-test reported only a singular rejection and mutated only
`AGENTS.md`; it did not prove an independent rejection for each of the six
guides.

## Initial GREEN

**Command**

```text
node scripts/verify-fix-branch-workflow.cjs --self-test
```

**Observed result**

```text
Fix-branch workflow documentation: 6 approved statements validated
Positive nested branch recommendation: rejected
```

### Round-2 GREEN

**Command**

```text
node --check scripts/verify-fix-branch-workflow.cjs
node scripts/verify-fix-branch-workflow.cjs --self-test
```

**Observed result**

```text
Fix-branch workflow documentation: 6 approved statements validated
Positive nested branch recommendations: 6/6 rejected
```

## Affected suite

**Command and result**

```text
node labtimesheet-docs-hub/ui-mockups/build-srs.cjs
node -e 'const fs=require("node:fs"); const checks=[["authoritative","labtimesheet-docs-hub/requirements-specification.md",/^\| ([A-Z]{2,4}-\d{3}) \|/gm],["explained","labtimesheet-docs-hub/explained/requirements-specification.md",/^\| ([A-Z]{2,4}-\d{3}) \|/gm],["simple","labtimesheet-docs-hub/explained/requirements-specification-simple.md",/^- \*\*([A-Z]{2,4}-\d{3}):\*\*/gm],["generated SRS","labtimesheet-docs-hub/software-requirements-specification.md",/^\| ([A-Z]{2,4}-\d{3}) \|/gm]]; for (const [name,file,pattern] of checks) { const ids=[...fs.readFileSync(file,"utf8").matchAll(pattern)].map(match=>match[1]); if (ids.length !== 260 || new Set(ids).size !== 260) throw new Error(`${name}: ${ids.length} rows, ${new Set(ids).size} unique`); console.log(`${name}: ${ids.length} rows, ${new Set(ids).size} unique IDs`); } const srs=fs.readFileSync("labtimesheet-docs-hub/software-requirements-specification.md","utf8"); const useCases=(srs.match(/^### 5\.\d+ UC-\d{2} —/gm)||[]).length; if (useCases !== 14) throw new Error(`SRS use cases: ${useCases}`); console.log(`generated SRS: ${useCases} use cases`);'
node -e 'const fs=require("node:fs"); const path=require("node:path"); let checked=0; const broken=[]; for (const file of process.argv.slice(1)) { const text=fs.readFileSync(file,"utf8"); for (const match of text.matchAll(/!?\[[^\]]*\]\(([^)]+)\)/g)) { const target=match[1].trim().replace(/^<|>$/g,"").split("#")[0].split("?")[0]; if (!target || /^[a-z][a-z0-9+.-]*:/i.test(target) || target.startsWith("//")) continue; checked += 1; if (!fs.existsSync(path.resolve(path.dirname(file), decodeURIComponent(target)))) broken.push(`${file}: ${target}`); } } if (broken.length) throw new Error(`Broken local Markdown links:\n${broken.join("\n")}`); console.log(`Local Markdown links: ${checked} resolved`);' AGENTS.md README.md DEVELOPMENT.md TESTING.md docs/superpowers/specs/2026-08-15-access-navigation-icon-intern-picker-design.md docs/superpowers/plans/2026-08-15-access-navigation-icon-intern-picker.md docs/tests/unit/fix-branch-workflow-documentation.md
cmp -s .agents/PROJECT_PLAN.md /Users/sechmachine/Documents/WebProjects/labtimesheet/.agents/PROJECT_PLAN.md && cmp -s .agents/skills/orchestrate-labtimesheet-iteration/SKILL.md /Users/sechmachine/Documents/WebProjects/labtimesheet/.agents/skills/orchestrate-labtimesheet-iteration/SKILL.md && cmp -s PRODUCT.md /Users/sechmachine/Documents/WebProjects/labtimesheet/PRODUCT.md && node -e 'const fs=require("node:fs"); const files=[".agents/PROJECT_PLAN.md",".agents/skills/orchestrate-labtimesheet-iteration/SKILL.md","PRODUCT.md"]; const forms=["work/fix/<feature>/<what-fix>","work/<feature>/fix/<what-fix>"]; for (const file of files) { const text=fs.readFileSync(file,"utf8"); for (const form of forms) { if (text.split(form).length !== 2) throw new Error(file+": expected one "+form); } } console.log("Root coordination authority: "+files.length+" approved branch rules match exactly");'
git diff --check 8be1b754e188367b260981718a5d33fc2d4d8a3b f98e7f39ef38c7882106ffb250155d2a72dcf0dd
```

The SRS regeneration and count assertion ran from the main root because the
ignored requirements hub is local authority there. The root-authority
comparisons, link assertion, and exact base-to-candidate `git diff --check`
ran from this fix worktree; the SRS generator also rejects a broken local SRS
target before it writes the generated file.

```text
Wrote labtimesheet-docs-hub/software-requirements-specification.md
Requirements: 260; use cases: 14; screens: 48; mockup embeds: 48
authoritative: 260 rows, 260 unique IDs
explained: 260 rows, 260 unique IDs
simple: 260 rows, 260 unique IDs
generated SRS: 260 rows, 260 unique IDs
generated SRS: 14 use cases
Fix-branch workflow documentation: 6 approved statements validated
Positive nested branch recommendations: 6/6 rejected
Root coordination authority: 3 approved branch rules match exactly
Local Markdown links: 7 resolved
git diff --check 8be1b754e188367b260981718a5d33fc2d4d8a3b f98e7f39ef38c7882106ffb250155d2a72dcf0dd: exit 0
```

## External-test boundaries

This documentation contract does not create or manipulate Git branches, start
the application, or replace branch-owner review. It validates the durable rule
and SRS traceability only; a taskmaster still authorizes branch creation,
integration, and any push.
