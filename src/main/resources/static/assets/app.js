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
});
