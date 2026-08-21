import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(path, "utf8");

test("terminal and transfer workflows require consequence confirmations", () => {
  const app = read("src/main/resources/static/assets/app.js");
  const project = read("src/main/resources/templates/projects/workflows.html");
  const task = read("src/main/resources/templates/tasks/detail.html");
  const transfer = read("src/main/resources/templates/fragments/workflows.html");
  const smtp = read("src/main/resources/templates/smtp/form.html");
  const settings = read("src/main/resources/templates/admin/settings.html");
  const accounts = read("src/main/resources/templates/accounts/index.html");

  assert.match(app, /form\[data-confirm\], form\[data-transfer-confirm\]/);
  assert.match(app, /Transfer \$\{tasks\.length\} selected Task/);
  assert.match(app, /window\.confirm\(message\)/);
  assert.match(project, /data-confirm="Approve this exit\?/);
  assert.match(project, /data-confirm="Directly remove this member\?/);
  assert.match(project, /data-confirm="Complete this Project\?/);
  assert.match(task, /data-confirm="Delete this Task from active views\?/);
  assert.match(transfer, /data-transfer-confirm/);
  assert.match(smtp, /data-confirm="Activate this tested SMTP draft and retire the current active revision\?/);
  assert.match(settings, /data-confirm="Activate this tested HolidayAPI draft and retire the current active revision\?/);
  assert.match(accounts, /data-confirm="Deactivate this account\?/);
  assert.match(accounts, /data-confirm="Complete this internship\?/);
  assert.match(accounts, /data-confirm="Withdraw this internship\?/);
});
