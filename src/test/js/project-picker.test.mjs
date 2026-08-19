import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

class Target {
  listeners = new Map();
  attributes = new Map();

  addEventListener(type, listener) {
    this.listeners.set(type, listener);
  }

  dispatch(type, event = {}) {
    this.listeners.get(type)?.({preventDefault() {}, target: this, ...event});
  }

  setAttribute(name, value) {
    this.attributes.set(name, value);
  }
}

test('picker searches name and student code, summarizes selection, and cancels safely', () => {
  const open = Object.assign(new Target(), {focus() { this.focused = true; }});
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
  const dialog = Object.assign(new Target(), {
    showModal() { this.open = true; },
    close() { this.open = false; this.dispatch('close'); },
  });
  const picker = {
    querySelector(selector) {
      return new Map([
        ['[data-picker-open]', open], ['[data-picker-dialog]', dialog],
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
  assert.equal(dialog.open, true);
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
  assert.equal(secondInput.checked, false);
  assert.equal(summary.textContent, 'No Interns selected');
  assert.equal(open.focused, true);

  open.dispatch('click');
  secondInput.checked = true;
  secondInput.dispatch('change');
  apply.dispatch('click');
  assert.equal(secondInput.checked, true);
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

test('leader dropdown filters eligible members and submits the selected user id', () => {
  const trigger = Object.assign(new Target(), {focus() { this.focused = true; }});
  const menu = {hidden: true};
  const search = Object.assign(new Target(), {value: '', focus() { this.focused = true; }});
  const valueInput = {value: ''};
  const valueLabel = {textContent: ''};
  const empty = {hidden: true};
  const options = [
    dropdownOption('21', 'Current Member (STU-021)', 'Current Member STU-021'),
    dropdownOption('22', 'Second Member (STU-022)', 'Second Member STU-022'),
  ];
  const dropdown = {
    contains(target) { return target === this || options.includes(target); },
    querySelector(selector) {
      return new Map([
        ['[data-leader-dropdown-trigger]', trigger], ['[data-leader-dropdown-menu]', menu],
        ['[data-leader-dropdown-search]', search], ['[data-leader-dropdown-value-input]', valueInput],
        ['[data-leader-dropdown-value]', valueLabel], ['[data-leader-dropdown-empty]', empty],
      ]).get(selector) ?? null;
    },
    querySelectorAll(selector) {
      return selector === '[data-leader-dropdown-option]' ? options : [];
    },
  };
  let ready;
  const documentListeners = new Map();
  const document = {
    documentElement: {dataset: {}, style: {}},
    addEventListener(type, listener) {
      if (type === 'DOMContentLoaded') ready = listener;
      else documentListeners.set(type, listener);
    },
    querySelector() { return null; },
    querySelectorAll(selector) { return selector === '[data-leader-dropdown]' ? [dropdown] : []; },
  };
  vm.runInNewContext(readFileSync('src/main/resources/static/assets/app.js', 'utf8'), {
    document,
    localStorage: {getItem() { return null; }, setItem() {}, removeItem() {}},
    matchMedia() { return {matches: false}; },
  });
  ready();

  trigger.dispatch('click');
  assert.equal(menu.hidden, false);
  assert.equal(search.focused, true);
  search.value = 'stu-022';
  search.dispatch('input');
  assert.equal(options[0].hidden, true);
  assert.equal(options[1].hidden, false);
  assert.equal(empty.hidden, true);

  options[1].dispatch('click');
  assert.equal(valueInput.value, '22');
  assert.equal(valueLabel.textContent, 'Second Member (STU-022)');
  assert.equal(menu.hidden, true);
  assert.equal(options[1].attributes.get('aria-selected'), 'true');

  documentListeners.get('click')?.({target: {}});
  assert.equal(menu.hidden, true);
});

function dropdownOption(value, label, search) {
  return Object.assign(new Target(), {
    hidden: false,
    dataset: {value, label, search},
  });
}
