document.addEventListener('DOMContentLoaded', () => {
  const root = document.documentElement;
  const theme = document.querySelector('[data-theme-select]');
  const stored = (() => {
    try { return localStorage.getItem('labtimesheet-theme') || 'system'; }
    catch (_) { return 'system'; }
  })();
  const systemPreference = matchMedia('(prefers-color-scheme: dark)');
  const applyTheme = (preference) => {
    const dark = preference === 'dark'
      || (preference === 'system' && systemPreference.matches);
    root.dataset.theme = dark ? 'dark' : 'light';
    root.style.colorScheme = dark ? 'dark' : 'light';
  };
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
      applyTheme(theme.value);
    });
    systemPreference.addEventListener?.('change', () => {
      if (theme.value === 'system') applyTheme('system');
    });
  }

  const roleField = document.querySelector('#role');
  const roleDependent = document.querySelector('[data-role-dependent]');
  if (roleField && roleDependent) {
    const applyRoleFields = () => {
      const isIntern = roleField.value === 'INTERN';
      roleDependent.hidden = !isIntern;
      roleDependent.querySelectorAll('input, select, textarea').forEach((input) => {
        input.disabled = !isIntern;
        if (!isIntern) input.value = '';
      });
    };
    roleField.addEventListener('change', applyRoleFields);
    applyRoleFields();
  }

  document.querySelectorAll('[data-task-version-toggle]').forEach((taskCheckbox) => {
    const versionInput = document.querySelector(`[data-task-version-for="${taskCheckbox.value}"]`);
    if (!versionInput) return;
    const syncTaskVersion = () => {
      versionInput.disabled = !taskCheckbox.checked;
    };
    taskCheckbox.addEventListener('change', syncTaskVersion);
    syncTaskVersion();
  });

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

  document.querySelectorAll('[data-drawer]').forEach((drawer) => {
    let opener = null;
    const close = drawer.querySelector('[data-drawer-close]');
    const restoreFocus = () => { opener?.focus(); opener = null; };
    document.querySelectorAll(`[data-drawer-open="${drawer.id}"]`).forEach((trigger) => {
      trigger.addEventListener('click', () => {
        opener = trigger;
        drawer.showModal();
        close?.focus();
      });
    });
    close?.addEventListener('click', () => drawer.close());
    drawer.addEventListener('cancel', () => restoreFocus());
    drawer.addEventListener('close', restoreFocus);
  });

  document.querySelectorAll('form[data-confirm], form[data-transfer-confirm]').forEach((form) => {
    form.addEventListener('submit', (event) => {
      let message = form.dataset.confirm;
      if (form.hasAttribute('data-transfer-confirm')) {
        const tasks = [...form.querySelectorAll('[name="taskIds"]:checked')];
        const firstTask = form.querySelector('[name="taskIds"]');
        if (tasks.length === 0) {
          event.preventDefault();
          firstTask?.setCustomValidity('Select at least one unfinished Task');
          firstTask?.reportValidity();
          return;
        }
        firstTask?.setCustomValidity('');
        const recipient = form.querySelector('[name="recipientMembershipId"]:checked')
          ?.closest('label')?.querySelector('span')?.textContent.trim();
        message = `Transfer ${tasks.length} selected Task${tasks.length === 1 ? '' : 's'} to ${recipient}? This batch commits immediately and remains after later cancellation or rejection.`;
      }
      if (!window.confirm(message)) event.preventDefault();
    });
  });

  document.querySelectorAll('[data-report-chart]').forEach((figure) => {
    if (typeof window.Chart !== 'function') return;
    const canvas = figure.querySelector('canvas');
    const rows = [...figure.querySelectorAll('.chart-data tbody tr')];
    const labels = rows.map((row) => row.cells[0].textContent.trim());
    const values = rows.map((row) => Number.parseFloat(row.cells[1].textContent.replace(',', '.')) || 0);
    const colors = () => {
      const styles = getComputedStyle(root);
      return ['--accent', '--muted', '--border'].map((name) => styles.getPropertyValue(name).trim());
    };
    const [accent, muted, border] = colors();
    const chart = new window.Chart(canvas, {
      type: 'line',
      data: { labels, datasets: [{
        data: values,
        borderColor: accent,
        pointBackgroundColor: accent,
        tension: 0,
      }] },
      options: {
        animation: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { ticks: { color: muted }, grid: { color: border } },
          y: { beginAtZero: true, ticks: { color: muted }, grid: { color: border } },
        },
      }
    });
    theme?.addEventListener('change', () => requestAnimationFrame(() => {
      const [nextAccent, nextMuted, nextBorder] = colors();
      chart.data.datasets[0].borderColor = nextAccent;
      chart.data.datasets[0].pointBackgroundColor = nextAccent;
      chart.options.scales.x.ticks.color = nextMuted;
      chart.options.scales.y.ticks.color = nextMuted;
      chart.options.scales.x.grid.color = nextBorder;
      chart.options.scales.y.grid.color = nextBorder;
      chart.update('none');
    }));
  });
});
