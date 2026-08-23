# Test Evidence: Local accessible Chart.js enhancement

- **Test type:** Web asset contract
- **Requirement IDs:** `I2-UI-05`, `UI-001`, `UI-004`, `UI-017`
- **Scenario IDs:** `AC-UI-001`, `AC-UI-004`, `AC-UI-005`
- **Test class/method:** `src/test/js/chart-contract.test.mjs#the accessible report table is progressively enhanced by pinned local Chart.js`
- **Implementation commit:** `pending local independent review`

## Protected behavior

The meaningful attendance trend uses exact local Chart.js `4.5.1`, loads before `app.js`, reads the adjacent accessible
table, disables animation, uses shared light/dark theme tokens, and updates colors without animation when the theme
changes. The table remains the complete alternative when JavaScript or canvas is unavailable.

## Test method

The Node standard-library test inspects the pinned manifest, build script, shared layout, and runtime enhancer. It
asserts the local asset order, no-animation configuration, theme variables, and no-animation theme update. `npm run
build` copies the dependency's reviewed UMD distribution into the application assets; no CDN or remote font is used.

## Hand-derived expected result

`chart.umd.min.js` precedes `app.js`; the chart consumes exactly the table's labels/percentages; animation remains
disabled initially and after a theme change; disabling JavaScript leaves the labeled table unchanged.

## RED

**Command**

```text
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin npm run test:ui
```

**Observed result**

```text
The new contract failed because `package.json` had no pinned `chart.js`, no `build:chart` step, and the shared layout
did not load a local Chart.js runtime. The existing enhancer therefore returned without rendering a chart.
```

## GREEN

**Command**

```text
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin npm run build
env PATH=/opt/homebrew/opt/node@24/bin:/usr/bin:/bin npm run test:ui
```

**Observed result**

```text
Node 24/npm 11; Tailwind 4.3.3 and local Chart.js asset build succeeded.
Tests: 7, Pass: 7, Fail: 0.
```

## Affected suite

**Command and result**

```text
The final Java 25 all-MVC suite passed 100/100, including the report table/canvas template contract. The wider
affected suite passed 334/334 with PostgreSQL 18.4 where applicable.
```

## External-test boundaries

This deterministic source/asset contract does not prove browser canvas rendering, contrast measurement, or keyboard
focus. The adjacent table is the required accessible source of truth; a real supported-desktop browser check remains
part of the integrated exit demonstration.
