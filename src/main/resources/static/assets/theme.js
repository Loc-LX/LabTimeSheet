(() => {
  const systemIsDark = () => window.matchMedia('(prefers-color-scheme: dark)').matches;
  let preference = 'system';
  try {
    preference = localStorage.getItem('labtimesheet-theme') || 'system';
  } catch (_) {
    // Browser privacy settings may disable storage; the system preference remains usable.
  }
  const dark = preference === 'dark' || (preference === 'system' && systemIsDark());
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
})();
