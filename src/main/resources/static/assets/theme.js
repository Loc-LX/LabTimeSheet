(() => {
  const systemPreference = window.matchMedia('(prefers-color-scheme: dark)');
  const systemIsDark = () => systemPreference.matches;
  let preference = 'system';
  try {
    preference = localStorage.getItem('labtimesheet-theme') || 'system';
  } catch (_) {
    // Browser privacy settings may disable storage; the system preference remains usable.
  }
  const dark = preference === 'dark' || (preference === 'system' && systemIsDark());
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
  systemPreference.addEventListener?.('change', () => {
    try {
      const saved = localStorage.getItem('labtimesheet-theme');
      if (saved && saved !== 'system') return;
    } catch (_) {
      // Continue following the system preference when storage is unavailable.
    }
    const nextDark = systemIsDark();
    document.documentElement.dataset.theme = nextDark ? 'dark' : 'light';
    document.documentElement.style.colorScheme = nextDark ? 'dark' : 'light';
  });
})();
