#!/usr/bin/env node
// Module boundary analysis for the proposed split of the feature packages.
//
//   node scripts/module-boundaries.cjs [repo-root] [--merge-internship] [--out <dir>]
//
// What it does
//   1. Assigns every canonical MODULE.md and nested feature SPEC.md rule to a module and
//      fails unless each rule lands in exactly one.
//   2. Collects dependency edges between target modules from three sources:
//      rule citations, dependencies stated only in rule prose (each phrase is
//      checked against the rule text), and type references in src/main/java.
//   3. Applies the recorded resolutions and reports the strongly connected groups
//      before and after, then the dependency layers.
//   4. Scans every rule for mentions of a concept owned by another module, and fails
//      unless each mention of the same or a higher layer carries a recorded verdict.
//      --out also writes concept-scan.tsv.
//
// Scope and lifetime
//   It describes the structure as it stands before the split. The class and method
//   tables below are judgments tied to the current packages, so once the code has
//   moved this script stops being accurate. The lasting guard is a cycle test in the
//   test suite; after the move either turn the cycle check into that test or retire
//   this script. Recorded in decision D28.
//
// Only Node built-ins are used. Adding a package would be a new dependency.
// cspell:ignore polic bnotif bauthenticat
'use strict';
const fs = require('fs');
const path = require('path');

const args = process.argv.slice(2);
const MERGE_INTERNSHIP = args.includes('--merge-internship');
const outIndex = args.indexOf('--out');
const OUT_DIR = outIndex >= 0 ? path.resolve(args[outIndex + 1]) : null;
const ROOT = path.resolve(args.find((a, i) => !a.startsWith('--') && args[i - 1] !== '--out') || '.');

const BASE_MODULES = ['identity', 'internship', 'calendar', 'attendance', 'project', 'reporting', 'notification', 'platform'];
const fold = m => (MERGE_INTERNSHIP && m === 'internship' ? 'identity' : m);
const MODULES = [...new Set(BASE_MODULES.map(fold))];

/* ---------------------------------------------------------------------------
 * 1. Rules -> module
 * A rule is a table row matching /^\| ([A-Z]+-\d{3}) \|/, the same pattern as
 * RULE_ROW in src/test/js/spec-structure-contract.test.mjs.
 * ------------------------------------------------------------------------- */
const specDir = path.join(ROOT, '.sdd/specs');
const rules = new Map();
for (const moduleName of BASE_MODULES) {
  const moduleDir = path.join(specDir, moduleName);
  const files = [path.join(moduleDir, 'MODULE.md'),
    ...fs.readdirSync(path.join(moduleDir, 'features')).sort()
      .map(name => path.join(moduleDir, 'features', name, 'SPEC.md'))];
  for (const file of files) {
    for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
      const m = line.match(/^\| ([A-Z]+-\d{3}) \| (.*) \|$/);
      if (m) {
        if (rules.has(m[1])) throw new Error(`Duplicate canonical rule: ${m[1]}`);
        rules.set(m[1], { spec: path.relative(specDir, file), text: m[2] });
      }
    }
  }
}
if (!rules.size) throw new Error('No canonical rules found');
const num = id => Number(id.split('-')[1]);
const pre = id => id.split('-')[0];
const inRange = (id, p, a, b) => pre(id) === p && num(id) >= a && num(id) <= b;

// [module, basis, predicate]
const RULE_ASSIGNMENT = [
  ['identity', 'accounts, installation, sign-in, logout and the account state machine (D35, D38)', id => inRange(id, 'ACC', 1, 18) || inRange(id, 'ACC', 27, 30) || inRange(id, 'SEC', 2, 7) || id === 'SEC-015' || id === 'DB-022'],
  ['internship', 'internship lifecycle, responsible Mentor and role integrity (D32)', id => inRange(id, 'ACC', 19, 26) || id === 'DB-021'],
  ['calendar', 'policy timeline (timezone, workdays, hours), global calendar, HolidayAPI', id =>
    inRange(id, 'ATT', 1, 3) || pre(id) === 'CAL' || id === 'DB-009' || id === 'INT-009'],
  ['attendance', 'punches, use of policy, periods, corrections, exceptions, leave', id =>
    inRange(id, 'ATT', 4, 24) || ['COR', 'EXC', 'LEV'].includes(pre(id)) || id === 'DB-002' || inRange(id, 'DB', 14, 18)],
  ['attendance', 'relocated unchanged: recipients of attendance events', id => id === 'NOT-011'],
  ['project', 'projects, membership, leadership, tasks, work logs', id =>
    ['PRJ', 'TSK'].includes(pre(id)) || inRange(id, 'AUTH', 5, 8) || inRange(id, 'DB', 11, 13) || inRange(id, 'DB', 19, 20)],
  ['project', 'relocated unchanged: authorization wholly about projects', id => ['AUTH-004', 'AUTH-009', 'AUTH-011'].includes(id)],
  ['project', 'relocated unchanged: recipients chosen from Project data', id => ['NOT-003', 'NOT-010'].includes(id)],
  ['reporting', 'reports and exports', id => pre(id) === 'RPT' || id === 'ERR-006'],
  ['notification', 'inbox, outbox, redelivery', id => (inRange(id, 'NOT', 1, 10) || id === 'NOT-012') && !['NOT-003', 'NOT-010'].includes(id)],
  ['platform', 'cross-cutting rules, raw mail and SMTP configuration, secrets', id =>
    ['GOV', 'ARC', 'OPS', 'TST', 'UI'].includes(pre(id))
    || inRange(id, 'ERR', 1, 5) || id === 'ERR-007'
    || id === 'SEC-001' || inRange(id, 'SEC', 8, 14)
    || inRange(id, 'AUTH', 1, 3) || id === 'AUTH-010' || id === 'AUTH-012'
    || id === 'DB-001' || inRange(id, 'DB', 3, 8) || id === 'DB-010'
    || inRange(id, 'INT', 1, 8) || id === 'INT-010'],
];
const ruleModule = new Map();
const ruleBasis = new Map();
const ruleProblems = [];
for (const id of rules.keys()) {
  const hits = RULE_ASSIGNMENT.filter(([, , match]) => match(id));
  if (hits.length !== 1) { ruleProblems.push(`${id}: ${hits.length} matches`); continue; }
  ruleModule.set(id, fold(hits[0][0]));
  ruleBasis.set(id, hits[0][1]);
}

/* ---------------------------------------------------------------------------
 * 2. Rule edges. An edge A -> B means module A needs something module B owns.
 * ------------------------------------------------------------------------- */
const edges = [];
const edge = (from, to, kind, evidence) => {
  from = fold(from); to = fold(to);
  if (from !== to) edges.push({ from, to, kind, evidence });
};
const expandReferences = text => {
  const ids = [];
  for (const m of text.matchAll(/([A-Z]+)-(\d{3})(?:`?\s*[–-]\s*`?(?:\1-)?(\d{3}))?/g)) {
    const first = Number(m[2]);
    const last = m[3] ? Number(m[3]) : first;
    for (let n = first; n <= last; n += 1) ids.push(`${m[1]}-${String(n).padStart(3, '0')}`);
  }
  return [...new Set(ids)];
};
for (const [id, { text }] of rules) {
  for (const cited of expandReferences(text)) {
    if (cited !== id && ruleModule.has(cited)) edge(ruleModule.get(id), ruleModule.get(cited), 'rule-citation', `${id} cites ${cited}`);
  }
}

// Dependencies stated only in prose. The phrase must occur verbatim in the rule.
// Business dates are not listed here: they are derived from the code in section 3,
// so that the reading of GOV-011 is applied to every module the same way.
const PROSE = [
  ['attendance', 'calendar', 'CAL-009', 'SHALL refuse attendance check-in on it', 'check-in needs the global day-off calendar'],
  ['project', 'calendar', 'CAL-009', 'SHALL refuse creating or moving a Task due date onto it', 'Task due dates need the global day-off calendar'],
  ['attendance', 'calendar', 'LEV-002', 'global days off', 'leave counts eligible workdays against the calendar'],
  ['attendance', 'internship', 'LEV-002', 'applicable internship interval', 'leave is bounded by the internship'],
  ['project', 'internship', 'PRJ-017', 'internship are `ACTIVE`', 'invitation and direct addition need internship state'],
  ['internship', 'project', 'ACC-022', 'holds a current leadership term or owns an unfinished Task', 'completion and withdrawal need open Project work'],
  ['internship', 'attendance', 'ACC-026', 'move every pending or overdue leave, correction, and attendance exception request', 'reassignment touches pending requests'],
  ['internship', 'identity', 'ACC-024', '`DEACTIVATED`', 'withdrawal deactivates the account'],
  ['internship', 'calendar', 'ACC-021', 'WHEN the configured internship start date is reached', 'activation needs the business date (GOV-011)'],
  ['identity', 'internship', 'ACC-023', 'allow authentication in read-only mode', 'a completed Intern signs in read-only'],
  ['identity', 'platform', 'ACC-011', 'no tested SMTP configuration', 'account creation needs an active SMTP configuration'],
  ['platform', 'identity', 'INT-008', 'send a message to that Admin', 'the SMTP test mails the Admin'],
  ['attendance', 'internship', 'DB-008', 'leave quota', 'quota validation locks the Intern profile'],
  ['project', 'internship', 'DB-008', 'daily work-minute total', 'work-log totals lock the Intern profile'],
  ['attendance', 'internship', 'ATT-018', 'terminal internship timestamp', 'attendance obligations end with the internship'],
  ['calendar', 'attendance', 'CAL-008', 'future-calendar impact preview', 'the preview must disclose leave reservations attendance owns'],
  ['calendar', 'project', 'CAL-007', 'with an impact preview', 'a new day off affects Task due dates (CAL-009)'],
];
const GOV_011_PHRASE = 'timezone of the applicable attendance policy version';
const proseProblems = [];
if (!rules.get('GOV-011')?.text.includes(GOV_011_PHRASE)) proseProblems.push('GOV-011: phrase not found, the business-date reading must be revisited');
for (const [from, to, id, phrase, meaning] of PROSE) {
  const rule = rules.get(id);
  if (!rule) proseProblems.push(`${id}: no such rule`);
  else if (!rule.text.includes(phrase)) proseProblems.push(`${id}: phrase not found: ${phrase}`);
  else edge(from, to, 'rule-prose', `${id} "${phrase}": ${meaning}`);
}

/* ---------------------------------------------------------------------------
 * 3. Code edges. Every file under src/main/java gets a module. Type names are
 * resolved as Java does: single-type import, same package, on-demand import.
 * Comments are removed and string literals kept, because JPQL names entities
 * inside strings. AccountService is split by method.
 * A business-date computation is an edge to calendar, the owner of the policy
 * timezone, following the text of GOV-011 rather than the BUSINESS_ZONE constant
 * the code uses today (constitution, known gap for GOV-011).
 * ------------------------------------------------------------------------- */
const INTERNSHIP_IN_ACCOUNT = new Set(['InternshipStatus', 'EligibleInternOption', 'InternReportingWindow', 'InternWorkWindow',
  'InternshipLifecycleGuard', 'InternProfile', 'InternProfileRepository', 'InternshipLifecycleScheduler', 'LockedAccountMutationEligibility']);
const CALENDAR_IN_ATTENDANCE = new Set(['CalendarController', 'AttendancePolicyController', 'CalendarException', 'PolicyException',
  'AttendancePolicy', 'AttendancePolicyCommand', 'AttendancePolicyHistoryItem', 'CalendarHistoryItem', 'CalendarImportSelection',
  'CalendarPreviewItem', 'GlobalCalendarEvent', 'AttendancePolicyEntity', 'GlobalCalendarEventEntity', 'AttendancePolicyRepository',
  'GlobalCalendarEventRepository', 'AttendancePolicyApplicationService', 'AttendancePolicyTimeline', 'CalendarApplicationService']);
const INTERNSHIP_METHODS = new Set(['activateInternship', 'activateDueInternships', 'completeInternship', 'withdrawInternship',
  'isEligibleIntern', 'historicalInternReportingWindow', 'lockedInternWorkWindow', 'eligibleInternOptions', 'requireEligibleIntern',
  'studentCodeByUserId', 'lockedAccountMutationEligibility']);
const BUSINESS_DATE = /LocalDate\.now\(|\.atZone\(|\bbusinessDate\(\)|\bcurrentBusinessDate\(\)/;

const javaRoot = path.join(ROOT, 'src/main/java');
const walk = dir => fs.readdirSync(dir, { withFileTypes: true })
  .flatMap(e => (e.isDirectory() ? walk(path.join(dir, e.name)) : e.name.endsWith('.java') ? [path.join(dir, e.name)] : []));
const javaFiles = walk(javaRoot);
const relative = file => path.relative(javaRoot, file).replace(/\\/g, '/');
const fqnOf = file => relative(file).replace(/\.java$/, '').replace(/\//g, '.');
const simpleName = fqn => fqn.split('.').pop();
const packageOf = fqn => fqn.split('.').slice(0, -1).join('.');

function moduleOfClass(fqn) {
  const parts = fqn.split('.');
  if (parts[3] !== 'feature') return 'composition'; // config/* and LabtimesheetApplication wire modules
  const cls = simpleName(fqn);
  switch (parts[4]) {
    case 'account': return fold(INTERNSHIP_IN_ACCOUNT.has(cls) ? 'internship' : 'identity');
    case 'attendance': return CALENDAR_IN_ATTENDANCE.has(cls) ? 'calendar' : 'attendance';
    // R4 puts the SMTP administration screen in identity, beside the bootstrap that offers SMTP setup.
    case 'integration': return cls.startsWith('HolidayApi') ? 'calendar' : cls === 'SmtpController' ? 'identity' : 'platform';
    case 'task':
    case 'project': return 'project';
    case 'reporting': return cls === 'AdminSettingsController' ? 'calendar' : cls === 'NotificationController' ? 'notification' : 'reporting';
    case 'notification': return 'notification';
    default: throw new Error(`unmapped class ${fqn}`);
  }
}
const byFqn = new Map(javaFiles.map(f => [fqnOf(f), f]));
const knownSimpleNames = new Set([...byFqn.keys()].map(simpleName));
const stripComments = src => src
  .replace(/\/\*[\s\S]*?\*\//g, block => block.replace(/[^\n]/g, ' '))
  .replace(/(^|[^:"])\/\/.*$/gm, '$1');

const intoComposition = [];
const businessDateSites = [];
const inferredInternshipHelpers = [];
for (const file of javaFiles) {
  const fqn = fqnOf(file);
  const src = stripComments(fs.readFileSync(file, 'utf8'));
  const lines = src.split('\n');
  const singleImports = new Map();
  const wildcardImports = [];
  for (const m of src.matchAll(/^import\s+(com\.lab\.labtimesheet\.[\w.]+(?:\.\*)?)\s*;/gm)) {
    if (m[1].endsWith('.*')) wildcardImports.push(m[1].slice(0, -2));
    else singleImports.set(simpleName(m[1]), m[1]);
  }
  const resolve = name => {
    if (singleImports.has(name)) return singleImports.get(name);
    const samePackage = `${packageOf(fqn)}.${name}`;
    if (byFqn.has(samePackage)) return samePackage;
    for (const w of wildcardImports) if (byFqn.has(`${w}.${name}`)) return `${w}.${name}`;
    return null;
  };
  const isAccountService = simpleName(fqn) === 'AccountService';
  const methodAt = [];
  // Methods on the internship side of AccountService: the named public ones, plus
  // every private helper whose callers all sit on the internship side. Inferred,
  // not listed by hand, so a helper cannot be misfiled by its name.
  const internshipSide = new Set(INTERNSHIP_METHODS);
  if (isAccountService) {
    let depth = 0;
    let current = null;
    const privateMethods = new Set();
    lines.forEach((line, i) => {
      if (depth === 1) {
        const m = line.match(/^\s*(public|private|protected)[^=;(]*\s(\w+)\s*\(/);
        if (m) { current = m[2]; if (m[1] === 'private') privateMethods.add(m[2]); }
      }
      methodAt[i] = current;
      for (const ch of line) {
        if (ch === '{') depth += 1;
        else if (ch === '}') { depth -= 1; if (depth === 1) current = null; }
      }
    });
    const callers = new Map();
    lines.forEach((line, i) => {
      for (const m of line.matchAll(/(?<![.\w])(\w+)\s*\(/g)) {
        if (!privateMethods.has(m[1]) || methodAt[i] === m[1] || !methodAt[i]) continue;
        if (!callers.has(m[1])) callers.set(m[1], new Set());
        callers.get(m[1]).add(methodAt[i]);
      }
    });
    for (let changed = true; changed;) {
      changed = false;
      for (const [helper, from] of callers) {
        if (!internshipSide.has(helper) && [...from].every(c => internshipSide.has(c))) { internshipSide.add(helper); changed = true; }
      }
    }
    inferredInternshipHelpers.push(...[...internshipSide].filter(m => !INTERNSHIP_METHODS.has(m)));
  }
  const moduleAt = i => (isAccountService && internshipSide.has(methodAt[i]) ? fold('internship') : moduleOfClass(fqn));
  const accountServiceVariables = new Set([...src.matchAll(/\bAccountService\s+(\w+)\s*[;,)=]/g)].map(m => m[1]));

  lines.forEach((line, i) => {
    if (/^\s*(package|import)\s/.test(line)) return;
    const from = moduleAt(i);
    const where = `${relative(file)}:${i + 1}`;
    if (BUSINESS_DATE.test(line) && from !== 'composition') {
      businessDateSites.push({ module: from, where });
      edge(from, 'calendar', 'business-date', `${where} (GOV-011 reading)`);
    }
    for (const m of line.matchAll(/\b([A-Z]\w*)\b/g)) {
      if (!knownSimpleNames.has(m[1]) || m[1] === simpleName(fqn) || m[1] === 'AccountService') continue;
      const target = resolve(m[1]);
      if (!target) continue;
      const to = moduleOfClass(target);
      if (to === 'composition' && from !== 'composition') intoComposition.push(`${from} <- ${where} ${m[1]}`);
      else if (from !== 'composition' && to !== 'composition') edge(from, to, 'code-type', `${where} ${m[1]}`);
    }
    for (const m of line.matchAll(/\b(\w+)(?:\(\))?\.(\w+)\s*\(/g)) {
      if (!accountServiceVariables.has(m[1]) && m[1] !== 'requireAccountService') continue;
      if (from === 'composition') continue;
      edge(from, INTERNSHIP_METHODS.has(m[2]) ? 'internship' : 'identity', 'code-call', `${where} AccountService.${m[2]}`);
    }
    if (isAccountService) {
      for (const m of line.matchAll(/(?<![.\w])(\w+)\s*\(/g)) {
        if (internshipSide.has(m[1]) && methodAt[i] !== m[1]) edge(from, 'internship', 'code-call', `${where} ${methodAt[i]} -> ${m[1]}`);
      }
    }
  });
}

/* ---------------------------------------------------------------------------
 * 4. Graph
 * ------------------------------------------------------------------------- */
function adjacency(list) {
  const adj = new Map(MODULES.map(m => [m, new Set()]));
  for (const e of list) if (adj.has(e.from) && adj.has(e.to) && e.from !== e.to) adj.get(e.from).add(e.to);
  return adj;
}
function stronglyConnected(list) {
  const adj = adjacency(list);
  let counter = 0;
  const stack = [];
  const onStack = new Set();
  const index = new Map();
  const low = new Map();
  const groups = [];
  const visit = v => {
    index.set(v, counter); low.set(v, counter); counter += 1;
    stack.push(v); onStack.add(v);
    for (const w of adj.get(v)) {
      if (!index.has(w)) { visit(w); low.set(v, Math.min(low.get(v), low.get(w))); }
      else if (onStack.has(w)) low.set(v, Math.min(low.get(v), index.get(w)));
    }
    if (low.get(v) === index.get(v)) {
      const group = [];
      let w;
      do { w = stack.pop(); onStack.delete(w); group.push(w); } while (w !== v);
      if (group.length > 1) groups.push(group.sort());
    }
  };
  for (const m of MODULES) if (!index.has(m)) visit(m);
  return groups;
}
function layers(list) {
  const adj = adjacency(list);
  const depth = new Map();
  const measure = m => {
    if (depth.has(m)) return depth.get(m);
    depth.set(m, 0);
    const d = adj.get(m).size ? 1 + Math.max(...[...adj.get(m)].map(measure)) : 0;
    depth.set(m, d);
    return d;
  };
  MODULES.forEach(measure);
  const byDepth = [];
  for (const [m, d] of depth) (byDepth[d] = byDepth[d] || []).push(m);
  return byDepth.map(g => g.sort());
}
const grouped = list => {
  const out = new Map();
  for (const e of list) {
    if (e.from === e.to || !MODULES.includes(e.from) || !MODULES.includes(e.to)) continue;
    const key = `${e.from} -> ${e.to}`;
    if (!out.has(key)) out.set(key, []);
    out.get(key).push(e);
  }
  return out;
};

/* ---------------------------------------------------------------------------
 * 5. Resolutions. None of them changes the text of a rule.
 * ------------------------------------------------------------------------- */
const INTERNSHIP = fold('internship');
const RESOLUTIONS = [
  { id: 'R1', title: 'internship <-> project',
    drop: e => (e.from === INTERNSHIP && e.to === 'project') || (e.from === 'identity' && e.to === 'project' && e.evidence.includes('AccountController')),
    add: [['project', 'internship']],
    how: 'internship declares a port asking whether an Intern still holds a leadership term or an unfinished Task (ACC-022); project implements it. The completion and withdrawal orchestration in ProjectService and AccountController moves to internship.' },
  { id: 'R2', title: 'internship <-> attendance',
    drop: e => e.from === INTERNSHIP && e.to === 'attendance',
    how: 'Requests store no assigned approver; the responsible Mentor is resolved when a decision is made. V1 forces decided_by_mentor_user_id to NULL while a request is PENDING. The new exception table keeps that shape.' },
  { id: 'R3', title: 'identity <-> internship', skipWhenMerged: true,
    drop: e => e.from === 'identity' && e.to === 'internship',
    add: [['internship', 'identity']],
    how: 'internship composes Intern creation, correction and the Student Code directory over identity in one transaction; identity keeps accounts and sign-in. ACC-023 read-only access is enforced by the authorization policy from internship state.' },
  { id: 'R4', title: 'platform <-> identity',
    drop: e => e.from === 'platform' && e.to === 'identity',
    add: [['identity', 'platform']],
    how: 'Platform services receive the verified actor and the recipient address; the SMTP administration screen sits in identity beside the bootstrap that offers SMTP setup (ACC-005, ACC-011).' },
  { id: 'R7', title: 'calendar -> attendance, who is the actor',
    drop: e => e.from === 'calendar' && e.to === 'attendance' && /AttendanceActor|AttendanceRole|AttendanceCurrentUserService/.test(e.evidence),
    how: 'AttendanceRole copies GlobalRole and AttendanceCurrentUserService only calls AccountService.requireIdentityByEmail. Calendar resolves its actor from identity.' },
  { id: 'R8', title: 'calendar -> attendance, business date',
    drop: e => e.from === 'calendar' && e.to === 'attendance' && /AttendanceApplicationService/.test(e.evidence),
    add: [['attendance', 'calendar']],
    how: 'currentBusinessDate is today in the policy timezone; the policy lives in calendar, so the business date is computed there.' },
  { id: 'R9', title: 'calendar -> attendance and project, change impact preview',
    drop: e => e.from === 'calendar' && /^CAL-00[78]/.test(e.evidence),
    add: [['attendance', 'calendar'], ['project', 'calendar']],
    how: 'Calendar declares a port that attendance and project each implement with the data they own (leave reservations, Task due dates). Not wired today: TaskQueryService.dueDateImpacts has no caller.' },
  { id: 'R6', title: 'platform -> business modules, rule references',
    drop: e => e.from === 'platform' && e.kind === 'rule-citation',
    how: 'AUTH-003, AUTH-010, GOV-014, GOV-015, ARC-010 and UI-019 cite module rules as definitions. The authorization policy receives scope resolved by the owning module (platform plan §5, second risk; §2.3 says the opposite and is corrected). The code graph shows no platform code importing a business module.' },
];

/* ---------------------------------------------------------------------------
 * Report
 * ------------------------------------------------------------------------- */
const out = [];
const say = s => out.push(s);
say(`# Module boundary analysis${MERGE_INTERNSHIP ? ' (internship merged into identity)' : ''}`);
say(`\n## Rules\nfound ${rules.size}, assigned ${ruleModule.size}, problems ${ruleProblems.length}`);
ruleProblems.forEach(p => say(`  PROBLEM ${p}`));
say(MODULES.map(m => `${m}=${[...ruleModule.values()].filter(v => v === m).length}`).join('  '));
say(`relocated beyond prefix and range: ${[...ruleBasis].filter(([, b]) => b.startsWith('relocated')).map(([id]) => `${id} (${rules.get(id).spec} -> ${ruleModule.get(id)})`).join(', ')}`);
say(`prose phrases checked: ${PROSE.length + 1}, problems ${proseProblems.length}`);
proseProblems.forEach(p => say(`  PROBLEM ${p}`));

say(`\nAccountService private helpers inferred to the internship side: ${inferredInternshipHelpers.sort().join(', ') || 'none'}`);
say(`\n## Business-date computations, by target module (GOV-011 reading)`);
const sitesByModule = {};
businessDateSites.forEach(s => { (sitesByModule[s.module] = sitesByModule[s.module] || []).push(s.where); });
Object.keys(sitesByModule).sort().forEach(m => say(`${m}: ${sitesByModule[m].length} site(s)`));

const before = grouped(edges);
say(`\n## Edges before resolution: ${edges.filter(e => MODULES.includes(e.from) && MODULES.includes(e.to)).length}`);
[...before].sort().forEach(([k, list]) => {
  const kinds = {};
  list.forEach(e => { kinds[e.kind] = (kinds[e.kind] || 0) + 1; });
  say(`${k.padEnd(28)} ${Object.entries(kinds).map(([a, b]) => `${a}:${b}`).join(' ')}`);
});
say(`\n## Cycles before resolution`);
stronglyConnected(edges).forEach(g => say(`  { ${g.join(', ')} }`));

let after = edges.slice();
say(`\n## Resolutions`);
for (const r of RESOLUTIONS) {
  if (MERGE_INTERNSHIP && r.skipWhenMerged) { say(`${r.id} ${r.title}: not applicable when merged`); continue; }
  const dropped = after.filter(r.drop).length;
  after = after.filter(e => !r.drop(e)).concat((r.add || []).map(([from, to]) => ({ from: fold(from), to: fold(to), kind: 'resolution', evidence: r.id })));
  say(`${r.id} ${r.title}: drops ${dropped}\n    ${r.how}`);
}
const remaining = stronglyConnected(after);
say(`\n## Cycles after resolution: ${remaining.length}`);
remaining.forEach(g => {
  say(`  { ${g.join(', ')} }`);
  const inGroup = grouped(after);
  for (const [k, list] of inGroup) {
    const [a, b] = k.split(' -> ');
    if (g.includes(a) && g.includes(b)) say(`    ${k}: ${list.slice(0, 3).map(e => `[${e.kind}] ${e.evidence}`).join(' | ')}`);
  }
});
if (!remaining.length) {
  say(`\n## Layers, bottom first`);
  layers(after).forEach((g, i) => say(`  ${i}: ${g.join(', ')}`));
}
say(`\n## Edges after resolution`);
[...grouped(after)].sort().forEach(([k, list]) => say(`  ${k.padEnd(28)} ${list.length}`));

/* ---------------------------------------------------------------------------
 * 6. Concept scan over every rule.
 * Each concept names the module that owns it. The dictionary is built from the
 * specification itself: the tables of the physical inventory, the terms of the
 * terminology table, and the actors and states the rules use. For every rule, every
 * mention of a concept owned by another module is listed with the layer relation.
 * A mention of a lower layer is the allowed direction. A mention of the same or a
 * higher layer needs a recorded verdict, and the run fails without one.
 * The scan is complete relative to this dictionary; whether the dictionary is
 * complete is a judgment, recorded in D28.
 * ------------------------------------------------------------------------- */
const CONCEPTS = [
  // [owner, label, pattern]
  ['identity', 'account tables', /`(?:app_users|user_action_tokens|system_state)`/],
  ['identity', 'account activation', /\bactivation (?:links?|tokens?|mails?|resends?|delivery|deliveries)\b|\baccount activation\b/i],
  ['identity', 'password', /\bpasswords?\b/i],
  ['identity', 'session', /\bsessions?\b/i],
  ['identity', 'sign-in', /\bsign(?:s|ed)?[- ]in\b|\blog(?:s|ged)?[- ]?in\b|\bauthenticat\w*/i],
  ['identity', 'bootstrap', /\bbootstrap\b/i],
  ['identity', 'account state', /`(?:PENDING_ACTIVATION|LOCKED|DEACTIVATED)`/],
  ['internship', 'intern profile', /`intern_profiles`|\bIntern profile\b|\bStudent Code\b/i],
  ['internship', 'internship', /\binternships?\b/i],
  ['internship', 'responsible Mentor', /\bresponsible Mentors?\b/i],
  ['calendar', 'policy tables', /`(?:attendance_policy_versions|attendance_policy_workdays|global_calendar_events)`/],
  ['calendar', 'attendance policy', /\b(?:attendance[- ])?policy versions?\b|\battendance polic(?:y|ies)\b/i],
  ['calendar', 'global calendar', /\bglobal (?:day|days) off\b|\bglobal calendar\b|\bcalendar events?\b|\bglobal events?\b/i],
  ['calendar', 'HolidayAPI', /\bHolidayAPI\b/],
  ['attendance', 'attendance tables', /`(?:attendance_records|attendance_corrections|attendance_correction_events|leave_requests|leave_request_days)`/],
  ['attendance', 'punch', /\bcheck[- ]?in\b|\bcheck[- ]?out\b/i],
  ['attendance', 'attendance record', /\battendance (?:row|record)s?\b/i],
  ['attendance', 'correction', /\bcorrections?\b/i],
  ['attendance', 'leave', /\bleave (?:request|quota|day|days|allocation)s?\b|\bapproved leave\b/i],
  ['attendance', 'attendance exception', /\battendance exceptions?\b|\bexcused\b/i],
  ['attendance', 'attendance period', /\battendance periods?\b|\bfinalized periods?\b/i],
  ['attendance', 'violation', /\blate arrivals?\b|\bearly departures?\b|\bmissing checkouts?\b|\bcompliance\b/i],
  ['project', 'project tables', /`(?:projects|project_memberships|project_leadership_terms|project_invitations|project_membership_exit_requests|tasks|task_comments|task_work_logs|task_remaining_effort_forecasts)`/],
  ['project', 'Project', /\bProjects?\b/],
  ['project', 'Task', /\bTasks?\b/],
  ['project', 'Leader', /\bLeaders?\b|\bleadership\b/],
  ['project', 'membership', /\bmemberships?\b|\bmembers?\b/i],
  ['project', 'invitation', /\binvitations?\b/i],
  ['project', 'work log', /\bwork[- ]logs?\b/i],
  ['project', 'effort', /\bestimates?\b|\bforecasts?\b|\bRemaining effort\b|\bCurrent Work\b/],
  ['project', 'owning Mentor', /\bowning Mentors?\b/i],
  ['reporting', 'report', /\breports?\b|\bexports?\b|\bXLSX\b|\bPDF\b|\bdashboards?\b/i],
  ['notification', 'notification', /\bnotif\w*|\bnotify\b|\boutbox\b|\bunread\b/i],
];
const LAYER = new Map();
const verdicts = new Map(); // key `${rule}>${module}` -> [kind, reason]
const VERDICT_KINDS = new Set(['dependency', 'vocabulary', 'homonym', 'reader-or-prohibition', 'reference']);
// Kinds: dependency (the reason names the resolution that removes it), vocabulary (a
// name shared across modules, no data read), homonym (the same word for a different
// thing), reader-or-prohibition (another module reads this module, or the rule forbids
// something and reads nothing), reference (a cross-cutting obligation each owning
// module implements; platform code holds nothing for it).
const VERDICTS = [
  // [rule, module whose concept is mentioned, kind, reason]
  ['ACC-017', 'internship', 'dependency', 'R3: internship composes the Student Code directory and Intern field correction over identity'],
  ['ACC-018', 'attendance', 'homonym', '"Admin email correction" corrects an account identity, not attendance'],
  ['ACC-022', 'project', 'dependency', 'R1: internship declares the readiness interface, project implements it'],
  ['ACC-023', 'attendance', 'reader-or-prohibition', 'attendance refuses the writes by reading internship state; internship reads nothing of attendance'],
  ['ACC-023', 'project', 'reader-or-prohibition', 'project refuses the writes by reading internship state; internship reads nothing of project'],
  ['ACC-024', 'attendance', 'reader-or-prohibition', 'withdrawal leaves attendance history attributable and reads none of it'],
  ['ACC-029', 'internship', 'reader-or-prohibition', 'reinstating an account is forbidden from restoring an internship; identity writes and reads nothing of internship'],
  ['ACC-029', 'project', 'reader-or-prohibition', 'reinstating an account is forbidden from restoring a membership or leadership term; identity writes and reads nothing of project'],
  ['ACC-024', 'project', 'reader-or-prohibition', 'withdrawal leaves Project history attributable and reads none of it'],
  ['ACC-026', 'attendance', 'dependency', 'R2: requests store no assigned approver, so reassignment moves nothing'],
  ['ATT-001', 'attendance', 'vocabulary', 'fields of the policy table calendar owns; attendance applies them (D28 keeps the table whole)'],
  ['ATT-002', 'attendance', 'vocabulary', 'seed values of the policy table calendar owns'],
  ['ATT-003', 'attendance', 'vocabulary', 'validation of fields of the policy table calendar owns'],
  ['ATT-004', 'reporting', 'reader-or-prohibition', 'attendance resolves the policy for a date; reporting reads the result'],
  ['ATT-012', 'reporting', 'reader-or-prohibition', 'forbids editing raw punches through any operation, including a report'],
  ['ATT-017', 'reporting', 'homonym', '"report `N/A`" means display the value, not the reporting module'],
  ['CAL-002', 'reporting', 'reader-or-prohibition', 'forbids calling HolidayAPI from a dashboard or report'],
  ['CAL-008', 'attendance', 'dependency', 'R9: the impact preview asks attendance through the calendar change impact interface; the waiver is enforced by attendance reading the calendar'],
  ['CAL-009', 'attendance', 'reader-or-prohibition', 'attendance refuses check-in by reading the calendar'],
  ['CAL-009', 'project', 'reader-or-prohibition', 'project refuses a due date by reading the calendar'],
  ['DB-013', 'attendance', 'homonym', '"correction reason" belongs to a Remaining effort forecast, not attendance'],
  ['EXC-005', 'reporting', 'reader-or-prohibition', 'reporting reads the excused flag attendance keeps'],
  ['EXC-006', 'project', 'reader-or-prohibition', 'only the responsible Mentor may decide: an allow-list, so no Project data is read'],
  ['LEV-004', 'reporting', 'homonym', '"dashboard" is the My Leave balance rendered by AttendanceRequestController; the reporting dashboard shows no leave'],
  ['NOT-002', 'attendance', 'vocabulary', 'NotificationType is owned by notification and chosen by the publisher; notification imports nothing of attendance'],
  ['NOT-002', 'project', 'vocabulary', 'NotificationType is owned by notification and chosen by the publisher; notification imports nothing of project'],
  ['TSK-021', 'attendance', 'homonym', '"no correction has superseded" a forecast, not attendance'],
  ['ARC-005', 'internship', 'reference', 'names the package in the structure rule'],
  ['ARC-005', 'notification', 'reference', 'names the package in the structure rule'],
  ['ARC-010', 'reporting', 'reference', 'every module that renders rows, reporting included, keeps queries independent of row count'],
  ['AUTH-001', 'internship', 'dependency', 'R6: internship resolves internship state into the context the policy receives'],
  ['AUTH-001', 'project', 'dependency', 'R6: project resolves ownership, membership, leadership and assignee into the context the policy receives'],
  ['AUTH-002', 'identity', 'reference', 'route protection is wired in config, which may depend on identity; each module returns the same not-found response'],
  ['AUTH-003', 'internship', 'dependency', 'R6: internship resolves the responsible Mentor into the context the policy receives'],
  ['AUTH-003', 'attendance', 'dependency', 'R6: attendance names the request kind in the context the policy receives'],
  ['AUTH-003', 'project', 'dependency', 'R6: project resolves the owning Mentor into the context the policy receives'],
  ['AUTH-010', 'project', 'reference', 'a pointer to RPT-005 that adds nothing of its own'],
  ['DB-003', 'internship', 'reference', 'a uniqueness constraint in the schema of a table internship owns'],
  ['DB-003', 'attendance', 'reference', 'uniqueness constraints in the schema of tables attendance owns'],
  ['DB-003', 'project', 'reference', 'uniqueness constraints in the schema of tables project owns'],
  ['DB-004', 'project', 'reference', 'composite foreign keys in the schema of tables project owns'],
  ['DB-006', 'project', 'reference', 'indexes on tables project owns'],
  ['DB-006', 'notification', 'reference', 'an index on the table notification owns'],
  ['DB-007', 'attendance', 'reference', 'attendance enforces its invariants inside its own transactions'],
  ['DB-007', 'project', 'reference', 'project enforces its invariants inside its own transactions'],
  ['DB-008', 'internship', 'reference', 'attendance and project lock the Intern profile through an internship service; platform code holds nothing'],
  ['DB-008', 'attendance', 'reference', 'attendance validates quota under that lock'],
  ['ERR-002', 'project', 'reference', 'the Leader is named as one actor whose decision each module protects with optimistic locking'],
  ['ERR-004', 'notification', 'reference', 'each scheduled worker avoids duplicating a notification'],
  ['ERR-005', 'calendar', 'reference', 'calendar keeps a HolidayAPI failure from blocking local data'],
  ['ERR-005', 'project', 'reference', 'project data stays available when mail or HolidayAPI fails'],
  ['GOV-002', 'project', 'vocabulary', 'fixes the term Project'],
  ['GOV-004', 'project', 'reference', 'the separation holds between attendance and project; no edge joins them, and AttendanceAndTaskWorkSeparationTest checks it'],
  ['GOV-005', 'attendance', 'reference', 'immutable policy versions in calendar and frozen snapshots in attendance keep past results'],
  ['GOV-007', 'identity', 'reader-or-prohibition', 'forbids JWT authentication'],
  ['GOV-007', 'reporting', 'reader-or-prohibition', 'forbids a persisted Report entity'],
  ['GOV-008', 'identity', 'reader-or-prohibition', 'non-goals; "authenticated invitation acceptance" names the only allowed workflow'],
  ['GOV-008', 'project', 'reader-or-prohibition', 'non-goals for Projects and Tasks'],
  ['GOV-009', 'identity', 'reader-or-prohibition', 'forbids persisting login-attempt history'],
  ['GOV-009', 'attendance', 'reference', 'attendance retains its own decision and reopen history'],
  ['GOV-009', 'project', 'reference', 'project retains its own leadership and Task transition history'],
  ['GOV-011', 'calendar', 'dependency', 'R8: the business date is computed in calendar; no platform service reads the policy'],
  ['GOV-012', 'attendance', 'reference', 'each module records server time as event time'],
  ['GOV-013', 'identity', 'reference', 'identity serializes bootstrap on its own record'],
  ['GOV-013', 'attendance', 'reference', 'attendance serializes quota through the internship lock of DB-008'],
  ['GOV-013', 'project', 'reference', 'project serializes Task transfers on its own records'],
  ['GOV-014', 'attendance', 'reference', 'attendance retains its rows'],
  ['GOV-014', 'project', 'reference', 'project retains its rows; the PRJ-002 deletion is its own'],
  ['GOV-014', 'notification', 'reference', 'notification retains its rows'],
  ['GOV-015', 'project', 'reader-or-prohibition', 'non-goals for Task effort planning'],
  ['INT-001', 'calendar', 'reference', 'calendar administers HolidayAPI settings in the application; platform does SMTP'],
  ['INT-003', 'identity', 'homonym', '"SMTP password" is an integration secret, not an account password'],
  ['INT-003', 'calendar', 'reference', 'calendar encrypts the HolidayAPI key with the platform cipher'],
  ['INT-004', 'identity', 'homonym', '"passwords" in Configuration History are SMTP secrets, not account passwords'],
  ['INT-005', 'identity', 'reader-or-prohibition', 'forbids an authentication token in logs, pages, exports and notification bodies'],
  ['INT-005', 'reporting', 'reader-or-prohibition', 'forbids a secret in an export'],
  ['INT-005', 'notification', 'reader-or-prohibition', 'forbids a secret in a notification body'],
  ['INT-007', 'identity', 'homonym', '"password" of the SMTP account, not an account password'],
  ['OPS-003', 'calendar', 'reference', 'tests do not depend on a real HolidayAPI key'],
  ['OPS-004', 'identity', 'reader-or-prohibition', 'forbids committing activation links and passwords'],
  ['OPS-004', 'calendar', 'reader-or-prohibition', 'forbids committing HolidayAPI keys'],
  ['OPS-006', 'calendar', 'reference', 'readiness does not require HolidayAPI'],
  ['OPS-006', 'reporting', 'homonym', '"report ready" is a health state, not the reporting module'],
  ['OPS-008', 'identity', 'homonym', '"password" of the datasource, not an account password'],
  ['OPS-009', 'calendar', 'reference', 'HolidayAPI credentials stay Admin configuration in calendar'],
  ['OPS-019', 'identity', 'homonym', '"agent session" is a contributor working context, not an authenticated application session'],
  // D30 retired OPS-020 and OPS-021; their old reporting/project mentions no longer exist.
  ['SEC-001', 'identity', 'reference', 'session authentication and password hashing are wired in config over identity accounts'],
  ['SEC-010', 'reporting', 'homonym', '"reports itself ready" is a health state'],
  ['SEC-011', 'identity', 'reference', 'session cookie flags are set in config'],
  ['TST-004', 'calendar', 'reference', 'tests fake HolidayAPI'],
  ['UI-003', 'identity', 'reference', 'navigation is built from authorization scope; the shell asks the policy'],
  ['UI-004', 'notification', 'reference', 'the header entry is a template; only DashboardController and NotificationController supply the unread count, and no shared advice reads notification'],
  ['UI-008', 'notification', 'reference', 'the notification menu is a template fragment with no Java dependency'],
  ['UI-011', 'project', 'reference', 'charts are rendered by the pages that show Project trends'],
  ['UI-016', 'internship', 'reference', 'the internship withdrawal screen uses the shared confirmation fragment'],
  ['UI-016', 'project', 'reference', 'Project and Task screens use the shared confirmation fragment'],
  ['UI-019', 'calendar', 'reference', 'the Attendance Policy and Global Calendar screens are calendar screens'],
  ['UI-019', 'attendance', 'reference', 'the leave, correction and exception workflows are attendance screens'],
  ['UI-019', 'project', 'reference', 'the invitation, exit and transfer workflows are project screens'],
  ['UI-019', 'reporting', 'reference', 'the screen inventory names the dashboard its module renders'],
];
// Findings of the reviewer's independent keyword scan. Each must appear in this run,
// either as a relocation or as a mention with a verdict; a missing one means the
// dictionary is incomplete, and the fix belongs in CONCEPTS, not in a new phrase.
const REVIEWER_FINDINGS = ['NOT-003', 'NOT-010', 'NOT-002', 'ACC-018', 'TSK-021', 'DB-013', 'ATT-004', 'ATT-012', 'ATT-017', 'EXC-005', 'CAL-002', 'EXC-006'];
for (const [rule, module, kind, reason] of VERDICTS) {
  if (!VERDICT_KINDS.has(kind)) throw new Error(`verdict ${rule}>${module}: unknown kind ${kind}`);
  verdicts.set(`${rule}>${module}`, [kind, reason]);
}
const scanRows = [];
const unresolved = [];
const verdictProblems = [];
if (!remaining.length) {
  layers(after).forEach((group, depth) => group.forEach(m => LAYER.set(m, depth)));
  for (const [id, { text }] of [...rules].sort()) {
    const own = ruleModule.get(id);
    const byModule = new Map();
    for (const [owner, label, pattern] of CONCEPTS) {
      const module = fold(owner);
      if (module === own) continue;
      const m = text.match(pattern);
      if (!m) continue;
      if (!byModule.has(module)) byModule.set(module, []);
      byModule.get(module).push(`${label} "${m[0]}"`);
    }
    for (const [module, hits] of byModule) {
      const relation = LAYER.get(module) < LAYER.get(own) ? 'lower' : LAYER.get(module) === LAYER.get(own) ? 'same' : 'higher';
      const verdict = verdicts.get(`${id}>${module}`);
      if (relation !== 'lower' && !verdict) unresolved.push(`${id} (${own}) mentions ${module} [${relation}]: ${hits.join('; ')}`);
      scanRows.push({ id, own, module, relation, hits: hits.join('; '), verdict: verdict ? verdict[0] : relation === 'lower' ? 'allowed direction' : '', reason: verdict ? verdict[1] : '' });
    }
  }
  const unused = [...verdicts.keys()].filter(k => !scanRows.some(r => `${r.id}>${r.module}` === k));
  say(`\n## Concept scan over ${rules.size} rules`);
  say(`dictionary entries: ${CONCEPTS.length}; mentions of another module: ${scanRows.length} (lower ${scanRows.filter(r => r.relation === 'lower').length}, same ${scanRows.filter(r => r.relation === 'same').length}, higher ${scanRows.filter(r => r.relation === 'higher').length})`);
  const kinds = {};
  scanRows.filter(r => r.relation !== 'lower' && r.verdict).forEach(r => { kinds[r.verdict] = (kinds[r.verdict] || 0) + 1; });
  say(`verdicts: ${Object.entries(kinds).map(([k, n]) => `${k} ${n}`).join(', ') || 'none'}`);
  say(`same or higher layer without a verdict: ${unresolved.length}`);
  unresolved.forEach(u => say(`  UNRESOLVED ${u}`));
  if (unused.length) say(`verdicts that match no mention (stale): ${unused.join(', ')}`);
  const resolutionIds = new Set(RESOLUTIONS.map(r => r.id));
  for (const [rule, module, kind, reason] of VERDICTS) {
    const named = (reason.match(/^(R\d+):/) || [])[1];
    if (kind === 'dependency' && !resolutionIds.has(named)) verdictProblems.push(`${rule}>${module}: dependency names no recorded resolution`);
  }
  for (const id of REVIEWER_FINDINGS) {
    const relocated = (ruleBasis.get(id) || '').startsWith('relocated');
    if (!relocated && !scanRows.some(r => r.id === id && r.relation !== 'lower')) verdictProblems.push(`${id}: reviewer finding not found by the scan`);
  }
  say(`reviewer findings reproduced: ${REVIEWER_FINDINGS.length - verdictProblems.filter(p => p.includes('reviewer finding')).length} of ${REVIEWER_FINDINGS.length}`);
  verdictProblems.forEach(p => say(`  PROBLEM ${p}`));
}

say(`\n## Composition root`);
say(`business modules referencing config: ${intoComposition.length}`);
intoComposition.forEach(x => say(`  ${x}`));
say('R10: SecurityProperties is shared configuration read by platform, not wiring; it moves to platform. Otherwise platform -> config -> identity -> platform.');
say(`verdict: ${intoComposition.every(x => x.includes('SecurityProperties')) ? 'every reference into config is covered by R10' : 'UNRESOLVED reference into config'}`);

console.log(out.join('\n'));
if (OUT_DIR) {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(path.join(OUT_DIR, 'rule-map.tsv'), `rule\tcurrent_spec\ttarget_module\tbasis\n${[...rules.keys()].sort().map(id => `${id}\t${rules.get(id).spec}\t${ruleModule.get(id)}\t${ruleBasis.get(id)}`).join('\n')}\n`);
  fs.writeFileSync(path.join(OUT_DIR, 'edges.tsv'), `from\tto\tkind\tevidence\n${edges.filter(e => MODULES.includes(e.from) && MODULES.includes(e.to)).map(e => `${e.from}\t${e.to}\t${e.kind}\t${e.evidence}`).join('\n')}\n`);
}
if (OUT_DIR) {
  fs.writeFileSync(path.join(OUT_DIR, 'concept-scan.tsv'), `rule\trule_module\tmentioned_module\tlayer_relation\tmentions\tverdict\treason\n${scanRows.map(r => [r.id, r.own, r.module, r.relation, r.hits, r.verdict, r.reason].join('\t')).join('\n')}\n`);
}
const staleVerdicts = [...verdicts.keys()].filter(k => !scanRows.some(r => `${r.id}>${r.module}` === k));
process.exitCode = ruleProblems.length || proseProblems.length || remaining.length || unresolved.length || staleVerdicts.length || verdictProblems.length ? 1 : 0;
