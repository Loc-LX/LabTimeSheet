document.addEventListener('DOMContentLoaded', () => {
  const root = document.documentElement;
  const theme = document.querySelector('[data-theme-select]');
  const stored = (() => {
    try { return localStorage.getItem('labtimesheet-theme') || 'system'; }
    catch (_) { return 'system'; }
  })();
  if (theme) {
    theme.value = stored;
    theme.addEventListener('change', () => {
      try {
        theme.value === 'system'
          ? localStorage.removeItem('labtimesheet-theme')
          : localStorage.setItem('labtimesheet-theme', theme.value);
      } catch (_) {
        // Theme still applies for this page when persistence is unavailable.
      }
      const dark = theme.value === 'dark'
        || (theme.value === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
      root.dataset.theme = dark ? 'dark' : 'light';
      root.style.colorScheme = dark ? 'dark' : 'light';
    });
  }

  let collapsed = false;
  try { collapsed = localStorage.getItem('labtimesheet-sidebar') === 'collapsed'; }
  catch (_) { /* Use the expanded default. */ }
  const sidebarToggle = document.querySelector('[data-sidebar-toggle]');
  const applySidebarState = () => {
    root.dataset.sidebarCollapsed = String(collapsed);
    sidebarToggle?.setAttribute('aria-expanded', String(!collapsed));
  };
  applySidebarState();
  sidebarToggle?.addEventListener('click', () => {
    collapsed = !collapsed;
    applySidebarState();
    try { localStorage.setItem('labtimesheet-sidebar', collapsed ? 'collapsed' : 'expanded'); }
    catch (_) { /* Collapse still works for this page. */ }
  });

  document.querySelectorAll('[data-intern-picker]').forEach((picker) => {
    const open = picker.querySelector('[data-picker-open]');
    const dialog = picker.querySelector('[data-picker-dialog]');
    const search = picker.querySelector('[data-picker-search]');
    const summary = picker.querySelector('[data-picker-summary]');
    const empty = picker.querySelector('[data-picker-empty]');
    const cancel = picker.querySelector('[data-picker-cancel]');
    const apply = picker.querySelector('[data-picker-apply]');
    const options = [...picker.querySelectorAll('[data-picker-option]')];
    let initialSelection = [];

    const inputs = () => options.map((option) => option.querySelector('input'));
    const updateSummary = () => {
      const selected = options
        .filter((option) => option.querySelector('input').checked)
        .map((option) => option.querySelector('[data-picker-label]').textContent.trim());
      summary.textContent = selected.length === 0
        ? `No Intern${inputs()[0]?.type === 'radio' ? '' : 's'} selected`
        : `${selected.length} Intern${selected.length === 1 ? '' : 's'} selected: ${selected.join(', ')}`;
    };
    const filter = () => {
      const query = search.value.trim().toLocaleLowerCase();
      let visible = 0;
      options.forEach((option) => {
        option.hidden = !option.dataset.pickerSearch.toLocaleLowerCase().includes(query);
        if (!option.hidden) visible += 1;
      });
      empty.hidden = visible !== 0;
    };
    const restore = () => {
      inputs().forEach((input, index) => { input.checked = initialSelection[index]; });
      updateSummary();
    };

    inputs().forEach((input) => input.addEventListener('change', updateSummary));
    search.addEventListener('input', filter);
    open.addEventListener('click', () => {
      initialSelection = inputs().map((input) => input.checked);
      search.value = '';
      filter();
      dialog.showModal();
      search.focus();
    });
    cancel.addEventListener('click', () => {
      restore();
      dialog.close();
    });
    dialog.addEventListener('cancel', restore);
    dialog.addEventListener('close', () => open.focus());
    apply.addEventListener('click', () => dialog.close());
    updateSummary();
  });

  document.querySelectorAll('[data-leader-dropdown]').forEach((dropdown) => {
    const trigger = dropdown.querySelector('[data-leader-dropdown-trigger]');
    const menu = dropdown.querySelector('[data-leader-dropdown-menu]');
    const search = dropdown.querySelector('[data-leader-dropdown-search]');
    const valueInput = dropdown.querySelector('[data-leader-dropdown-value-input]');
    const valueLabel = dropdown.querySelector('[data-leader-dropdown-value]');
    const empty = dropdown.querySelector('[data-leader-dropdown-empty]');
    const options = [...dropdown.querySelectorAll('[data-leader-dropdown-option]')];
    if (!trigger || !menu || !search || !valueInput || !valueLabel || !empty) return;

    // Đóng danh sách và cập nhật trạng thái trợ năng của nút mở dropdown.
    const close = () => {
      menu.hidden = true;
      trigger.setAttribute('aria-expanded', 'false');
    };

    // Đồng bộ tên hiển thị, giá trị hidden gửi về server và option đang chọn.
    const syncSelection = () => {
      const initial = options.find((option) => option.dataset.selected === 'true');
      if (!valueInput.value && initial) valueInput.value = initial.dataset.value;
      const selected = options.find((option) => option.dataset.value === valueInput.value);
      valueLabel.textContent = selected?.dataset.label || 'Choose a current member';
      options.forEach((option) => {
        option.setAttribute('aria-selected', String(option === selected));
      });
    };

    // Lọc theo tên hoặc Student Code, không thay đổi danh sách hợp lệ server đã trả về.
    const filter = () => {
      const query = search.value.trim().toLocaleLowerCase();
      let visible = 0;
      options.forEach((option) => {
        const matches = option.dataset.search.toLocaleLowerCase().includes(query);
        option.hidden = !matches;
        if (matches) visible += 1;
      });
      empty.hidden = visible !== 0;
    };

    trigger.addEventListener('click', () => {
      const opening = menu.hidden;
      menu.hidden = !opening;
      trigger.setAttribute('aria-expanded', String(opening));
      if (opening) {
        search.value = '';
        filter();
        search.focus();
      }
    });
    search.addEventListener('input', filter);
    options.forEach((option) => option.addEventListener('click', () => {
      valueInput.value = option.dataset.value || '';
      syncSelection();
      close();
      trigger.focus();
    }));
    document.addEventListener('click', (event) => {
      if (!dropdown.contains(event.target)) close();
    });
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && !menu.hidden) {
        close();
        trigger.focus();
      }
    });

    syncSelection();
    filter();
  });
});
