document.addEventListener('DOMContentLoaded', () => {
  const prefersReducedMotion = () =>
    typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
  const cssVariable = (name) => {
    try {
      return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || undefined;
    } catch (_) {
      return undefined;
    }
  };
  const themeTokens = () => ({
    text: cssVariable('--color-foreground'),
    muted: cssVariable('--color-muted'),
    grid: cssVariable('--color-border'),
  });

  const buildConfig = (spec, tokens) => {
    const config = {
      type: spec.type,
      data: spec.data,
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {legend: {labels: {color: tokens.text}}},
        scales: {
          x: {ticks: {color: tokens.muted}, grid: {color: tokens.grid}},
          y: {ticks: {color: tokens.muted}, grid: {color: tokens.grid}},
        },
      },
    };
    if (prefersReducedMotion()) {
      config.options.animation = false;
    }
    return config;
  };

  const render = (container) => {
    const canvas = container.querySelector('canvas');
    if (!canvas) return;
    let spec;
    try {
      spec = JSON.parse(container.dataset.chart);
    } catch (_) {
      return;
    }
    const chart = new Chart(canvas, buildConfig(spec, themeTokens()));
    container._chart = {chart, spec};
  };

  document.querySelectorAll('[data-chart]').forEach(render);

  if (typeof MutationObserver !== 'undefined') {
    const observer = new MutationObserver(() => {
      document.querySelectorAll('[data-chart]').forEach((container) => {
        const state = container._chart;
        if (!state) return;
        const tokens = themeTokens();
        state.chart.options.plugins.legend.labels.color = tokens.text;
        state.chart.options.scales.x.ticks.color = tokens.muted;
        state.chart.options.scales.x.grid.color = tokens.grid;
        state.chart.options.scales.y.ticks.color = tokens.muted;
        state.chart.options.scales.y.grid.color = tokens.grid;
        state.chart.update();
      });
    });
    observer.observe(document.documentElement, {attributes: true, attributeFilter: ['data-theme']});
  }
});