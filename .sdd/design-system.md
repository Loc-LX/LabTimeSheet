---
name: Lab Timesheet
description: A quiet, high-density operations system for internship attendance and Project work.
colors:
  ink: "#15171a"
  canvas: "#f6f7f8"
  sidebar: "#f0f1f2"
  panel: "#ffffff"
  panel-muted: "#f7f8f9"
  border: "#dfe1e5"
  border-strong: "#c9cdd3"
  text-muted: "#626a75"
  text-subtle: "#818894"
  accent: "#3157e7"
  success: "#087a48"
  warning: "#996000"
  danger: "#b42318"
typography:
  headline:
    fontFamily: "ui-sans-serif, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif"
    fontSize: "25px"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.025em"
  body:
    fontFamily: "ui-sans-serif, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.45
  label:
    fontFamily: "ui-sans-serif, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif"
    fontSize: "11px"
    fontWeight: 650
    lineHeight: 1.45
rounded:
  control: "8px"
  tab: "9px"
  surface: "12px"
  dialog: "14px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
components:
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.panel}"
    rounded: "{rounded.control}"
    padding: "8px 13px"
    height: "37px"
  input:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "9px 10px"
    height: "39px"
  panel:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink}"
    rounded: "{rounded.surface}"
---

# Design System: Lab Timesheet

## Overview

**Creative North Star: "The Calm Operations Ledger"**

Lab Timesheet is a permission-aware operations workspace. It favors legible state, compact controls, clear ownership, and reviewable records over decorative dashboard theater. The Vercel/shadcn-style reference is translated into server-rendered Thymeleaf surfaces with a stable shell and one role-correct task per page.

The visual system is quiet but not empty: thin structure, disciplined spacing, tabular data, and rare semantic color make consequential actions and deadlines easy to find. Example content must always be identified as illustrative.

**Key Characteristics:**

- Cool near-white surfaces with charcoal text.
- Fixed desktop sidebar and compact page header.
- Dense tables and direct forms as the main working surfaces.
- Black primary actions; color reserved for focus, status, warning, and error.
- Rounded corners and restrained ambient shadow, never floating card mosaics.

## Colors

The light palette uses cool neutral layers; dark mode must be designed from the supplied near-black references rather than mechanically inverted from these values.

### Primary

- **Operational Ink** (`#15171a`): primary text, brand mark, and primary actions.
- **Controlled Accent** (`#3157e7`): focus, selected data, and rare contextual emphasis.

### Neutral

- **Work Canvas** (`#f6f7f8`): page background.
- **Navigation Shell** (`#f0f1f2`): desktop sidebar.
- **Record Surface** (`#ffffff`): forms, tables, panels, and active navigation.
- **Muted Surface** (`#f7f8f9`): headers, tabs, and supporting rows.
- **Ledger Border** (`#dfe1e5`): default one-pixel structure.
- **Control Border** (`#c9cdd3`): inputs and action outlines.
- **Muted Text** (`#626a75`) and **Subtle Text** (`#818894`): secondary and tertiary copy.

### Semantic

- **Success** (`#087a48`), **Warning** (`#996000`), and **Danger** (`#b42318`) communicate state with text and shape, never color alone.

**The Rare Color Rule.** Neutral structure carries the interface. Semantic and accent colors appear only when they clarify state, focus, validation, or a consequential decision.

## Typography

**Display and Body Font:** the local system sans stack (`ui-sans-serif`, platform UI fonts, `Segoe UI`, sans-serif). No remote font is required.

**Character:** compact, familiar, and operational. Weight and spacing establish hierarchy without oversized marketing display type.

### Hierarchy

- **Page headline** (700, `25px`, 1.2): one per screen.
- **Panel title** (600–700, `14px`): names the current dataset or decision surface.
- **Body** (400, `14px`, 1.45): instructions and explanatory copy, normally no wider than 72ch.
- **Data** (500–650, `12px`): dense tables and facts; numeric summaries use tabular numerals.
- **Label** (650, `11px`): fields and supporting metadata.
- **Navigation group label** (750, `10px`, uppercase, `0.08em`): rare structural labels only.

**The One Headline Rule.** Each screen gets one page headline; hierarchy below it is compact and task-oriented.

## Layout

Desktop is the supported product target. The shell uses a 236px sidebar and a minimum-width content column, with a 60px header and 24–26px content inset. The implementation target may round the sidebar to approximately 16rem and its collapsed rail to approximately 4rem.

Content uses a four-cell metric strip, full-width table/form panels, and an occasional two-column decision or form/detail layout. The primary record or decision remains in the first viewport at 1365×900. Tables may scroll horizontally inside their own region but must not create page-level overflow.

Mobile and tablet responsiveness is best-effort only. It may reflow or scroll to avoid preventable breakage, but it is not required to provide complete workflow parity and has no dedicated mockup set.

## Elevation & Depth

Structure comes primarily from surface contrast and one-pixel borders. The only recurring ambient shadow is a soft panel lift (`0 10px 28px rgba(20,25,35,.06)`); active navigation and tabs use a smaller `0 1px 2px` shadow. Deep stacks and card-within-card effects are not part of this world.

**The Flat-First Rule.** Use border and tone before shadow. Shadow confirms grouping; it does not turn every region into a floating card.

## Shapes

Controls use an 8px radius, segmented containers 9px, primary panels 12px, and centered dialogs 14px. Status badges may be fully rounded because their small silhouette communicates state. Larger containers are not pill-shaped. Borders are neutral and one pixel.

## Components

### Buttons

- **Primary:** Operational Ink background, white text, 8px radius, 37px minimum height.
- **Secondary:** white background, stronger neutral border, same geometry.
- **Danger:** pale danger surface with explicit consequence copy; destructive actions require confirmation.
- **Focus:** visible high-contrast focus treatment is mandatory in implementation.

### Tables and panels

- Panels use white surface, one-pixel border, 12px radius, and optional ambient shadow.
- Table headers use muted surface, compact uppercase labels, and stable desktop columns.
- Status is shown with a text badge plus a non-color cue.
- Empty, unavailable, stale, and access-denied states replace the table body with direct operational copy.

### Inputs and forms

- Fields use white surface, stronger neutral border, 8px radius, and 39px minimum height.
- Labels stay visible; placeholder text never replaces a label.
- Validation preserves safe input, associates field errors, and adds a form-level error summary.
- Form actions appear once, at the end of the form; list/detail actions live in the page header.

### Navigation

- The desktop sidebar shows only authorized destinations.
- Active navigation uses a white surface and ink text without a colored stripe.
- The lower account area exposes profile, theme, and logout.
- Icon-only collapsed navigation requires accessible names and tooltips.

### Metric strips and tabs

- Metric strips are one bounded row divided by one-pixel rules, not separate floating cards.
- Tabs are compact segmented controls; a tab labels a true view switch, not a decorative category badge.

## Do's and Don'ts

### Do:

- **Do** put the user’s current task, deadline, record state, and authorized action in the first viewport.
- **Do** use server-authoritative dates/times and honest illustrative-data labels.
- **Do** keep role, ownership, membership, leadership, and assignee distinctions visible in copy and action placement.
- **Do** supply accessible labels, keyboard focus, error summaries, and chart text/table alternatives.

### Don't:

- **Don't** use gradients, glass effects, remote fonts, decorative charts, or oversized marketing headings.
- **Don't** use cards as the default container for every piece of content.
- **Don't** expose an action merely because the current global role sounds powerful enough; contextual authorization wins.
- **Don't** treat the desktop mockups as mobile requirements or imply mobile workflow parity.
- **Don't** invent production endorsements, adoption metrics, or unlabeled example records.
