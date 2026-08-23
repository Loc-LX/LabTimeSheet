import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = 'src/main/resources/static/assets/app-charts.js';

function runCharts({reduced = false, tokens = {}} = {}) {
  const instances = [];
  let ready;
  const canvas = {dataset: {}};
  const container = {
    dataset: {
      chart: JSON.stringify({
        type: 'line',
        data: {labels: ['A', 'B'], datasets: [{data: [1, 2]}]},
      }),
    },
    querySelector(selector) {
      return selector === 'canvas' ? canvas : null;
    },
  };
  const document = {
    documentElement: {dataset: {}, style: {}},
    addEventListener(type, listener) {
      if (type === 'DOMContentLoaded') ready = listener;
    },
    querySelectorAll(selector) {
      return selector === '[data-chart]' ? [container] : [];
    },
  };
  const context = {
    document,
    matchMedia() {
      return {matches: reduced};
    },
    getComputedStyle() {
      return {getPropertyValue: (name) => tokens[name] ?? ''};
    },
    MutationObserver: class {
      observe() {}
    },
    Chart: class Chart {
      constructor(element, config) {
        instances.push({element, config});
      }
    },
  };
  vm.runInNewContext(readFileSync(source, 'utf8'), context);
  return {ready, instances};
}

test('chart config maps theme tokens and honors reduced motion', () => {
  const {ready, instances} = runCharts({
    reduced: true,
    tokens: {
      '--color-foreground': '#0f172a',
      '--color-muted': '#64748b',
      '--color-border': '#e2e8f0',
    },
  });
  ready();

  assert.equal(instances.length, 1);
  const config = instances[0].config;
  assert.equal(config.data.labels[0], 'A');
  assert.equal(config.data.datasets[0].data[0], 1);
  assert.equal(config.options.plugins.legend.labels.color, '#0f172a');
  assert.equal(config.options.scales.x.ticks.color, '#64748b');
  assert.equal(config.options.scales.y.grid.color, '#e2e8f0');
  assert.equal(config.options.animation, false);
});

test('chart animates when the user has no reduced-motion preference', () => {
  const {ready, instances} = runCharts({reduced: false, tokens: {}});
  ready();

  assert.equal(instances.length, 1);
  assert.equal(instances[0].config.options.animation, undefined);
});

test('invalid chart JSON is skipped without throwing', () => {
  let ready;
  const container = {dataset: {chart: 'not-json'}, querySelector: () => ({})};
  const document = {
    documentElement: {dataset: {}, style: {}},
    addEventListener(type, listener) {
      if (type === 'DOMContentLoaded') ready = listener;
    },
    querySelectorAll(selector) {
      return selector === '[data-chart]' ? [container] : [];
    },
  };
  const context = {
    document,
    matchMedia() {
      return {matches: false};
    },
    getComputedStyle() {
      return {getPropertyValue: () => ''};
    },
    MutationObserver: class {
      observe() {}
    },
    Chart: class Chart {},
  };
  vm.runInNewContext(readFileSync(source, 'utf8'), context);
  ready();
});