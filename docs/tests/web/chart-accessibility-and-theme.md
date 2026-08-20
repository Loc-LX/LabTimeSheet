# Test Evidence: Chart.js theme tokens, reduced motion, and accessible fallback contract

- **Test type:** Web (frontend unit contract via `node --test`)
- **Requirement IDs:** `UI-011`, `UI-012`, `UI-017`
- **Scenario IDs:** `AC-UI-004`, `AC-UI-005`
- **Test class/method:** `src/test/js/chart-config.test.mjs` (three cases)
- **Implementation commit:** pending

## Protected behavior

Chart.js is used only for meaningful attendance and Project trends; every canvas must have an
accessible name and an adjacent text/table summary, and charts must consume theme tokens and honor
reduced motion (UI-011, UI-012). The `app-charts.js` initializer must never be the only
representation of a value. A failure here would render chart text in unreadable default black on a
dark canvas, animate charts for users who requested reduced motion, or throw on malformed page data.

## Test method

The `app-charts.js` script is executed in an isolated `vm` context with a controlled `document`
containing one `[data-chart]` container holding a canvas and a JSON payload, a fake `Chart`
constructor that records `(element, config)`, a fake `getComputedStyle` returning theme-token
variables, and a fake `matchMedia` for the reduced-motion flag. The tests assert the config handed to
Chart: theme tokens mapped into legend/ticks/grid colors, animation disabled only under reduced
motion, data/labels passed through unchanged, and malformed JSON skipped without throwing. This is the
narrowest production-shaped contract for the chart initializer because it exercises the exact
browser-facing script without a browser.

## Hand-derived expected result

- With `prefers-reduced-motion: reduce`: `config.options.animation === false`.
- Without it: animation left at the Chart.js default (`undefined`).
- Theme tokens: `--color-foreground` → `plugins.legend.labels.color`;
  `--color-muted` → `scales.x/y.ticks.color`; `--color-border` → `scales.x/y.grid.color`.
- Payload `{"type":"line","data":{labels:['A','B'],datasets:[{data:[1,2]}]}}` is passed through
  unchanged to `config.type` and `config.data`.
- Payload `not-json` produces no `Chart` construction and no exception.

## RED

**Command**

```text
npm run test:ui
```

**Observed result**

```text
✖ chart config maps theme tokens and honors reduced motion
  Error: ENOENT: no such file or directory, open
  'D:\SWP_BL5\labtimesheet\src\main\resources\static\assets\app-charts.js'
✖ invalid chart JSON is skipped without throwing
  Error: ENOENT: no such file or directory, open
  '.../app-charts.js'
```

The tests fail because no chart initializer script exists — the expected missing behavior.

## GREEN

**Command**

```text
npm run test:ui
```

**Observed result**

```text
✔ chart config maps theme tokens and honors reduced motion
✔ chart animates when the user has no reduced-motion preference
✔ invalid chart JSON is skipped without throwing
✔ picker searches name and student code, summarizes selection, and cancels safely
ℹ tests 4
ℹ pass 4
ℹ fail 0
```

## Affected suite

**Command and result**

```text
npm run build
```

```text
> build:icons
> node src/main/frontend/build-icons.mjs
> build:chart
> node src/main/frontend/build-chart.mjs
```

`src/main/resources/static/assets/chart.umd.js` is produced (208,518 bytes), proving the pinned
`chart.js@4.5.1` copy step and the committed `package-lock.json`.

## External-test boundaries

This test does not prove pixel rendering, real browser layout, or the Canvas `aria-label` presence in
page templates (that is the report-page web contract). It deliberately fakes `Chart`, the DOM, and
`matchMedia`, and does not run a browser.