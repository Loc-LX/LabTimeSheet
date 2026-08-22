import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

test('desktop shell includes a narrow-screen overflow safeguard', () => {
  const css = fs.readFileSync('src/main/frontend/app.css', 'utf8');
  assert.match(css, /@media\s*\(max-width:\s*64rem\)/);
  assert.match(css, /\.app-shell/);
  assert.match(css, /\.table-scroll/);
});
