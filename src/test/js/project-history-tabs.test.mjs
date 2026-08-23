import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

class Target {
  attributes = new Map();
  listeners = new Map();

  constructor(dataset = {}) {
    this.dataset = dataset;
  }

  addEventListener(type, listener) {
    this.listeners.set(type, listener);
  }

  dispatch(type, key) {
    let prevented = false;
    this.listeners.get(type)?.({key, preventDefault() { prevented = true; }});
    return prevented;
  }

  getAttribute(name) {
    return this.attributes.get(name) ?? null;
  }

  setAttribute(name, value) {
    this.attributes.set(name, value);
  }

  focus() {
    this.focused = true;
  }
}

test('project history shows one balanced history section at a time', () => {
  const template = readFileSync('src/main/resources/templates/projects/workflows.html', 'utf8');
  assert.match(template, /data-history-tabs/);
  assert.match(template, /data-history-tab="memberships"/);
  assert.match(template, /data-history-tab="leadership"/);
  assert.match(template, /data-history-tab="invitations"/);
  assert.match(template, /data-history-tab="exit-decisions"/);
  assert.match(template, /history-table-four/);
  assert.match(template, /history-table-five/);
  assert.match(template, /history-table-six/);

  const tabs = [
    tab('memberships', true),
    tab('leadership', false),
    tab('invitations', false),
    tab('exit-decisions', false),
  ];
  const panels = [
    panel('memberships'),
    panel('leadership'),
    panel('invitations'),
    panel('exit-decisions'),
  ];
  const historyTabs = {
    querySelectorAll(selector) {
      if (selector === '[data-history-tab]') return tabs;
      if (selector === '[data-history-panel]') return panels;
      return [];
    },
  };
  let ready;
  const document = {
    documentElement: {dataset: {}, style: {}},
    addEventListener(type, listener) { if (type === 'DOMContentLoaded') ready = listener; },
    querySelector() { return null; },
    querySelectorAll(selector) {
      return selector === '[data-history-tabs]' ? [historyTabs] : [];
    },
  };

  vm.runInNewContext(readFileSync('src/main/resources/static/assets/app.js', 'utf8'), {
    document,
    localStorage: {getItem() { return null; }, setItem() {}, removeItem() {}},
    matchMedia() { return {matches: false}; },
  });
  ready();

  assert.equal(panels[0].hidden, false);
  assert.equal(panels[1].hidden, true);
  assert.equal(tabs[0].getAttribute('aria-selected'), 'true');

  tabs[2].dispatch('click');
  assert.equal(panels[2].hidden, false);
  assert.equal(panels[0].hidden, true);
  assert.equal(tabs[2].getAttribute('aria-selected'), 'true');
  assert.equal(tabs[0].tabIndex, -1);

  assert.equal(tabs[2].dispatch('keydown', 'ArrowRight'), true);
  assert.equal(panels[3].hidden, false);
  assert.equal(tabs[3].focused, true);
});

function tab(name, selected) {
  const result = new Target({historyTab: name});
  result.setAttribute('aria-selected', String(selected));
  return result;
}

function panel(name) {
  return Object.assign(new Target({historyPanel: name}), {hidden: false});
}
