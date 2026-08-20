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

  removeAttribute(name) {
    this.attributes.delete(name);
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

test('member dropdown searches eligible Interns and keeps multiple selections', () => {
  const trigger = Object.assign(new Target(), {focus() { this.focused = true; }});
  const menu = {hidden: true};
  const search = Object.assign(new Target(), {value: '', focus() { this.focused = true; }});
  const valueLabel = {textContent: ''};
  const empty = {hidden: true};
  const done = new Target();
  const firstInput = Object.assign(new Target(), {checked: false, type: 'checkbox'});
  const secondInput = Object.assign(new Target(), {checked: false, type: 'checkbox'});
  const validationAlert = {hidden: false};
  const validationError = {hidden: false};
  const options = [
    memberOption('Nguyen An STU-020', 'Nguyen An (STU-020)', firstInput),
    memberOption('Tran Binh STU-021', 'Tran Binh (STU-021)', secondInput),
  ];
  trigger.setAttribute('aria-invalid', 'true');
  trigger.setAttribute('aria-describedby', 'member-intern-user-error');
  const form = {
    querySelector(selector) {
      return selector === '[data-member-validation-alert]' ? validationAlert : null;
    },
  };
  const dropdown = {
    contains(target) {
      return target === this || target === trigger || target === menu || target === search || target === done || options.includes(target);
    },
    closest() { return form; },
    querySelector(selector) {
      return new Map([
        ['[data-member-dropdown-trigger]', trigger], ['[data-member-dropdown-menu]', menu],
        ['[data-member-dropdown-search]', search], ['[data-member-dropdown-value]', valueLabel],
        ['[data-member-dropdown-empty]', empty], ['[data-member-dropdown-done]', done],
        ['[data-member-validation-error]', validationError],
      ]).get(selector) ?? null;
    },
    querySelectorAll(selector) {
      return selector === '[data-member-dropdown-option]' ? options : [];
    },
  };
  let ready;
  const document = {
    documentElement: {dataset: {}, style: {}},
    addEventListener(type, listener) { if (type === 'DOMContentLoaded') ready = listener; },
    querySelector() { return null; },
    querySelectorAll(selector) { return selector === '[data-member-dropdown]' ? [dropdown] : []; },
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
  search.value = 'stu-021';
  search.dispatch('input');
  assert.equal(options[0].hidden, true);
  assert.equal(options[1].hidden, false);

  secondInput.checked = true;
  secondInput.dispatch('change');
  assert.equal(valueLabel.textContent, '1 Intern selected: Tran Binh (STU-021)');
  assert.equal(options[1].attributes.get('aria-selected'), 'true');
  assert.equal(validationAlert.hidden, true);
  assert.equal(validationError.hidden, true);
  assert.equal(trigger.attributes.has('aria-invalid'), false);
  assert.equal(trigger.attributes.has('aria-describedby'), false);

  done.dispatch('click');
  assert.equal(menu.hidden, true);
  assert.equal(trigger.focused, true);
});

function memberOption(searchValue, label, input) {
  return Object.assign(new Target(), {
    hidden: false,
    dataset: {search: searchValue},
    querySelector(selector) {
      if (selector === 'input') return input;
      if (selector === '[data-member-dropdown-label]') return {textContent: label};
      return null;
    },
  });
}

test('leader dropdown filters eligible members and submits the selected user id', () => {
  const trigger = Object.assign(new Target(), {focus() { this.focused = true; }});
  const menu = {hidden: true};
  const search = Object.assign(new Target(), {value: '', focus() { this.focused = true; }});
  const valueInput = {value: ''};
  const valueLabel = {textContent: ''};
  const empty = {hidden: true};
  const validationAlert = {hidden: false};
  const validationError = {hidden: false};
  const options = [
    dropdownOption('21', 'Current Member (STU-021)', 'Current Member STU-021'),
    dropdownOption('22', 'Second Member (STU-022)', 'Second Member STU-022'),
  ];
  trigger.setAttribute('aria-invalid', 'true');
  trigger.setAttribute('aria-describedby', 'leadership-intern-user-error');
  const form = {
    querySelector(selector) {
      return selector === '[data-leader-validation-alert]' ? validationAlert : null;
    },
  };
  const dropdown = {
    contains(target) { return target === this || options.includes(target); },
    closest() { return form; },
    querySelector(selector) {
      return new Map([
        ['[data-leader-dropdown-trigger]', trigger], ['[data-leader-dropdown-menu]', menu],
        ['[data-leader-dropdown-search]', search], ['[data-leader-dropdown-value-input]', valueInput],
        ['[data-leader-dropdown-value]', valueLabel], ['[data-leader-dropdown-empty]', empty],
        ['[data-leader-validation-error]', validationError],
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
  assert.equal(validationAlert.hidden, true);
  assert.equal(validationError.hidden, true);
  assert.equal(trigger.attributes.has('aria-invalid'), false);
  assert.equal(trigger.attributes.has('aria-describedby'), false);

  documentListeners.get('click')?.({target: {}});
  assert.equal(menu.hidden, true);
});

function dropdownOption(value, label, search) {
  return Object.assign(new Target(), {
    hidden: false,
    dataset: {value, label, search},
  });
}
