import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

test('account creation marks role-dependent fields for safe client clearing', () => {
  const template = fs.readFileSync('src/main/resources/templates/accounts/new.html', 'utf8');
  const app = fs.readFileSync('src/main/resources/static/assets/app.js', 'utf8');
  assert.match(template, /data-role-dependent/);
  assert.match(app, /roleField\.value === 'INTERN'/);
  assert.match(app, /input\.value = ''/);
  assert.match(app, /input\.disabled/);
});

test('transfer form enables only the selected task version pair', () => {
  const template = fs.readFileSync('src/main/resources/templates/fragments/workflows.html', 'utf8');
  const app = fs.readFileSync('src/main/resources/static/assets/app.js', 'utf8');
  assert.match(template, /data-task-version-for/);
  assert.match(template, /disabled th:attr="data-task-version-for/);
  assert.match(app, /data-task-version-for/);
  assert.match(app, /versionInput\.disabled = !taskCheckbox\.checked/);

  const controls = [
    { name: 'taskIds', value: '41', checked: true },
    { name: 'taskVersions', value: '41:3', disabled: false },
    { name: 'taskIds', value: '42', checked: false },
    { name: 'taskVersions', value: '42:5', disabled: true }
  ];
  const successful = controls.filter(control =>
    control.name === 'taskIds' ? control.checked : !control.disabled);
  assert.deepEqual(successful.map(control => `${control.name}=${control.value}`), [
    'taskIds=41',
    'taskVersions=41:3'
  ]);
});
