import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

class Target {
  attributes = new Map();
  listeners = new Map();

  addEventListener(type, listener) {
    this.listeners.set(type, listener);
  }

  dispatch(type) {
    this.listeners.get(type)?.({preventDefault() {}, target: this});
  }

  getAttribute(name) {
    return this.attributes.get(name) ?? null;
  }

  setAttribute(name, value) {
    this.attributes.set(name, value);
  }
}

test('picker searches name and student code, summarizes selection, and cancels safely', () => {
  const open = Object.assign(new Target(), {textContent: 'Choose eligible Interns', focus() { this.focused = true; }});
  const cancel = new Target();
  const apply = new Target();
  const search = Object.assign(new Target(), {value: '', focus() { this.focused = true; }});
  const summary = {textContent: ''};
  const empty = {hidden: true};
  const firstInput = Object.assign(new Target(), {checked: false, type: 'checkbox'});
  const secondInput = Object.assign(new Target(), {checked: false, type: 'checkbox'});
  const options = [
    option('Nguyen An STU-020', 'Nguyen An (STU-020)', firstInput),
    option('Tran Binh STU-021', 'Tran Binh (STU-021)', secondInput),
  ];
  const dropdown = Object.assign(new Target(), {hidden: true});
  const picker = {
    querySelector(selector) {
      return new Map([
        ['[data-picker-open]', open], ['[data-picker-dialog]', dropdown],
        ['[data-picker-search]', search], ['[data-picker-summary]', summary],
        ['[data-picker-empty]', empty], ['[data-picker-cancel]', cancel],
        ['[data-picker-apply]', apply],
      ]).get(selector) ?? null;
    },
    querySelectorAll(selector) {
      return selector === '[data-picker-option]' ? options : [];
    },
  };
  let ready;
  const document = {
    documentElement: {dataset: {}, style: {}},
    addEventListener(type, listener) { if (type === 'DOMContentLoaded') ready = listener; },
    querySelector() { return null; },
    querySelectorAll(selector) { return selector === '[data-intern-picker]' ? [picker] : []; },
  };
  vm.runInNewContext(readFileSync('src/main/resources/static/assets/app.js', 'utf8'), {
    document,
    localStorage: {getItem() { return null; }, setItem() {}, removeItem() {}},
    matchMedia() { return {matches: false}; },
  });
  ready();

  open.dispatch('click');
  assert.equal(dropdown.hidden, false);
  assert.equal(open.getAttribute('aria-expanded'), 'true');
  assert.equal(search.focused, true);

  search.value = 'stu-021';
  search.dispatch('input');
  assert.equal(options[0].hidden, true);
  assert.equal(options[1].hidden, false);
  assert.equal(empty.hidden, true);

  secondInput.checked = true;
  secondInput.dispatch('change');
  assert.equal(summary.textContent, '1 Intern selected: Tran Binh (STU-021)');

  cancel.dispatch('click');
  assert.equal(dropdown.hidden, true);
  assert.equal(open.getAttribute('aria-expanded'), 'false');
  assert.equal(secondInput.checked, false);
  assert.equal(summary.textContent, 'No Interns selected');
  assert.equal(open.focused, true);

  open.dispatch('click');
  secondInput.checked = true;
  secondInput.dispatch('change');
  apply.dispatch('click');
  assert.equal(secondInput.checked, true);
});

test('single-select picker puts the selected Intern in the dropdown trigger', () => {
  const open = Object.assign(new Target(), {textContent: 'Choose an eligible Intern', focus() {}});
  const cancel = new Target();
  const apply = new Target();
  const search = Object.assign(new Target(), {value: '', focus() {}});
  const summary = {textContent: '', hidden: false};
  const empty = {hidden: true};
  const input = Object.assign(new Target(), {checked: false, type: 'radio'});
  const options = [option('Intern 1 DEMO-INT-1', 'Intern 1 (DEMO-INT-1)', input)];
  const dropdown = Object.assign(new Target(), {hidden: true});
  const picker = {
    querySelector(selector) {
      return new Map([
        ['[data-picker-open]', open], ['[data-picker-dialog]', dropdown],
        ['[data-picker-search]', search], ['[data-picker-summary]', summary],
        ['[data-picker-empty]', empty], ['[data-picker-cancel]', cancel],
        ['[data-picker-apply]', apply],
      ]).get(selector) ?? null;
    },
    querySelectorAll(selector) {
      return selector === '[data-picker-option]' ? options : [];
    },
  };
  let ready;
  const document = {
    documentElement: {dataset: {}, style: {}},
    addEventListener(type, listener) { if (type === 'DOMContentLoaded') ready = listener; },
    querySelector() { return null; },
    querySelectorAll(selector) { return selector === '[data-intern-picker]' ? [picker] : []; },
  };
  vm.runInNewContext(readFileSync('src/main/resources/static/assets/app.js', 'utf8'), {
    document,
    localStorage: {getItem() { return null; }, setItem() {}, removeItem() {}},
    matchMedia() { return {matches: false}; },
  });
  ready();

  input.checked = true;
  input.dispatch('change');

  assert.equal(open.textContent, 'Intern 1 (DEMO-INT-1)');
  assert.equal(summary.hidden, true);
});

function option(searchValue, label, input) {
  return {
    hidden: false,
    dataset: {pickerSearch: searchValue},
    querySelector(selector) {
      if (selector === 'input') return input;
      if (selector === '[data-picker-label]') return {textContent: label};
      return null;
    },
  };
}
