const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repositoryRoot = path.resolve(__dirname, '..');
const validForm = '`work/fix/<feature>/<what-fix>`';
const invalidForm = '`work/<feature>/fix/<what-fix>`';
const documents = [
  {
    file: 'AGENTS.md',
    approved: '- A targeted repair uses a clean, isolated `work/fix/<feature>/<what-fix>` branch and worktree from the taskmaster-verified current `main`. Do not use `work/<feature>/fix/<what-fix>`: the persistent `work/<feature>` ref already occupies that Git ref prefix.'
  },
  {
    file: 'README.md',
    approved: 'For a targeted repair, create a clean isolated branch and worktree from the\ntaskmaster-verified current `main` named\n`work/fix/<feature>/<what-fix>`. Do not nest it as\n`work/<feature>/fix/<what-fix>`: the persistent `work/<feature>` ref already\nuses that Git ref prefix.'
  },
  {
    file: 'DEVELOPMENT.md',
    approved: 'For a targeted repair, start a clean worktree from the taskmaster-verified\ncurrent `main` on `work/fix/<feature>/<what-fix>`. Keep it separate from the\nfive persistent `work/<feature>` branches. Do not use\n`work/<feature>/fix/<what-fix>` because the persistent `work/<feature>` ref\nalready occupies that Git ref prefix.'
  },
  {
    file: 'TESTING.md',
    approved: 'Run that check from the clean targeted-fix branch named\n`work/fix/<feature>/<what-fix>` when repairing one feature. Do not use\n`work/<feature>/fix/<what-fix>`: a persistent `work/<feature>` ref already\noccupies that Git ref prefix. Record the expected RED and the matching GREEN\nshell output in the evidence record.'
  },
  {
    file: 'docs/superpowers/specs/2026-08-15-access-navigation-icon-intern-picker-design.md',
    approved: 'Use `work/fix/<feature>/<what-fix>` for each targeted repair. Create its clean,\nisolated worktree from the taskmaster-verified current `main`. The `<feature>`\nsegment identifies the owning persistent area; it does not nest below that\npersistent branch.\n\nThe forbidden form is `work/<feature>/fix/<what-fix>`. A repair owner preserves\nother worktrees, records RED and GREEN evidence, commits locally, and does not\npush or merge without separate authority.'
  },
  {
    file: 'docs/superpowers/plans/2026-08-15-access-navigation-icon-intern-picker.md',
    approved: '2. Add one branch rule everywhere contributors, coordinators, and requirement\n   reviewers rely on it: `work/fix/<feature>/<what-fix>` from verified `main`.\n3. State why `work/<feature>/fix/<what-fix>` is invalid while its persistent\n   `work/<feature>` ref exists.'
  }
];

/** Validates that every tracked guide contains only its approved branch wording. */
function validate(contents) {
  const failures = [];

  for (const {file, approved} of documents) {
    const content = contents.get(file);
    if (count(content, validForm) !== 1) failures.push(`${file} must contain ${validForm} exactly once`);
    if (!content.includes(approved)) failures.push(`${file} is missing its approved branch workflow statement`);

    const outsideApprovedStatement = content.replace(approved, '');
    if (outsideApprovedStatement.includes(validForm) || outsideApprovedStatement.includes(invalidForm)) {
      failures.push(`${file} contains an unapproved branch-form reference`);
    }
  }

  if (failures.length) throw new Error(failures.join('\n'));
}

function count(content, value) {
  return content.split(value).length - 1;
}

function readContents() {
  return new Map(documents.map(({file}) => [file, fs.readFileSync(path.join(repositoryRoot, file), 'utf8')]));
}

const contents = readContents();
validate(contents);

if (process.argv.includes('--self-test')) {
  for (const {file} of documents) {
    const positiveRecommendation = new Map(contents);
    positiveRecommendation.set(file, `${contents.get(file)}\nUse ${invalidForm} for a targeted repair.\n`);
    assert.throws(
      () => validate(positiveRecommendation),
      (error) => error instanceof Error
        && error.message.includes(`${file} contains an unapproved branch-form reference`)
    );
  }
}

console.log(`Fix-branch workflow documentation: ${documents.length} approved statements validated`);
if (process.argv.includes('--self-test')) {
  console.log(`Positive nested branch recommendations: ${documents.length}/${documents.length} rejected`);
}
