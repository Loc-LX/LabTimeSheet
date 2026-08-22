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
