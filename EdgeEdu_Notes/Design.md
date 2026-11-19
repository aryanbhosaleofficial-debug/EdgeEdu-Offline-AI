---
title: Design System
aliases: [UI Design, Design Tokens, Visual Design]
tags: [design, ui, css, tailwind, typography, theming]
created: 2026-07-26
---

# 🎨 Design System

> Back to [[00 - Project Overview]] · See also [[03 - Frontend Deep Dive]]

---

## Design Philosophy

The UI follows an **editorial / ink-on-cream** aesthetic — think premium magazine layout rather than typical SaaS dashboard. Key principles:

1. **Warm, paper-like feel** — oklch cream/ink palette, not cold greys
2. **Serif headings, sans body** — Instrument Serif for titles, Work Sans for text
3. **Monospace accents** — JetBrains Mono for labels, badges, metadata
4. **Subtle texture** — optional SVG fractal-noise grain overlay
5. **Minimal, sharp radii** — `rounded-sm` (4px) over heavy rounding

---

## Colour Palette

### Light Mode

| Token | Value (oklch) | Hex Approx. | Usage |
|-------|---------------|-------------|-------|
| `--background` | `0.965 0.012 85` | `#f5f0e8` | Page background (warm cream) |
| `--foreground` | `0.18 0.03 265` | `#1a1a2e` | Primary text (deep ink) |
| `--ink` | `0.18 0.03 265` | `#1a1a2e` | Alias for foreground |
| `--cream` | `0.965 0.012 85` | `#f5f0e8` | Alias for background |
| `--paper` | `0.985 0.008 85` | `#faf8f3` | Card/surface background |
| `--accent` | `0.72 0.17 55` | `#d4a04a` | Warm amber accent |
| `--muted` | `0.92 0.012 85` | `#e8e3da` | Muted surface |
| `--muted-foreground` | `0.45 0.02 265` | `#6b6b7b` | Secondary text |
| `--border` | `0.85 0.015 80` | `#d4cfc5` | Borders |
| `--destructive` | `0.55 0.2 25` | `#d63031` | Error/danger |

### Dark Mode

| Token | Value (oklch) | Usage |
|-------|---------------|-------|
| `--background` | `0.16 0.02 265` | Deep dark blue-black |
| `--foreground` | `0.94 0.01 85` | Light cream text |
| `--paper` | `0.21 0.025 265` | Slightly lighter surface |
| `--accent` | `0.78 0.16 55` | Brighter amber |
| `--muted` | `0.26 0.025 265` | Dark muted surface |
| `--border` | `0.32 0.02 265` | Subtle dark borders |

---

## Typography

### Font Stack

| Role | Font | Fallback | Usage |
|------|------|----------|-------|
| **Display** | Instrument Serif | Georgia, serif | h1, h2, h3, `.font-display` |
| **Body** | Work Sans | system-ui, sans-serif | All body text |
| **Mono** | JetBrains Mono | monospace | Labels, badges, metadata, code |

### Font Weights
- Work Sans: 300 (light), 400 (regular), 500 (medium), 600 (semibold)
- JetBrains Mono: 400 (regular), 500 (medium)
- Instrument Serif: 400 only (no bold — the typeface is designed for elegant weight)

### Typography Rules
```css
h1, h2, h3, .font-display {
    font-family: 'Instrument Serif';
    font-weight: 400;
    letter-spacing: -0.02em;
}
```

---

## Spacing & Border Radius

### Border Radius Scale
| Token | Value | Usage |
|-------|-------|-------|
| `sm` | 4px | Default for cards, buttons, inputs |
| `md` | 8px | Larger containers |
| `lg` | 14px | Modals, feature cards |
| `xl` | 22px | Badges, pills |

> The design deliberately uses **small radii** (`rounded-sm`) as the default. This creates sharp, editorial edges rather than bubbly SaaS aesthetics.

---

## Component Design

### Card
```
rounded-sm border border-border/70 bg-paper p-6
shadow-[0_2px_12px_-4px_rgba(20,20,40,0.07)]
```
- Paper background with subtle warm shadow
- Thin border at 70% opacity

### Button Variants

| Variant | Style |
|---------|-------|
| **primary** | `bg-ink text-cream hover:opacity-90` |
| **ghost** | `border border-ink/20 bg-transparent hover:border-ink/60` |
| **danger** | `bg-destructive text-destructive-foreground hover:opacity-90` |

Common: `rounded-sm px-4 py-2.5 text-sm font-medium transition`

### Input
```
rounded-sm border border-border bg-paper px-3 py-2.5
focus:border-accent focus:ring-1 focus:ring-accent
```

### Label
```
font-mono text-[11px] uppercase tracking-[0.15em] text-muted-foreground
```
Labels use monospace, all-caps with wide letter spacing — a magazine-inspired detail.

### Badge
```
rounded-full px-2.5 py-0.5 font-mono text-[10px] uppercase tracking-wider
```

| Colour | Style |
|--------|-------|
| `slate` | `bg-muted text-muted-foreground` |
| `green` | `bg-accent/20 text-accent-foreground` |
| `red` | `bg-destructive/15 text-destructive` |
| `amber` | `bg-accent/25 text-accent-foreground` |
| `indigo` | `bg-accent/20 text-accent-foreground` |

---

## Section Headers

Dashboard and school pages use a distinctive section header pattern:

```tsx
<div className="font-mono text-[11px] uppercase tracking-[0.22em] text-muted-foreground">
  § 01 — Dashboard
</div>
<h1 className="mt-3 font-display text-5xl leading-tight">Your schools</h1>
<p className="mt-3 max-w-lg text-muted-foreground">
  Set up a school once, then generate conflict-free timetables in one pass.
</p>
```

The section symbol (`§`) + numbering + em dash creates an editorial chapter-marker feel.

---

## Special Effects

### Grain Overlay
```css
.grain-overlay::after {
    background-image: var(--grain);  /* SVG fractalNoise filter */
    opacity: 0.5;
    mix-blend-mode: multiply;
}
```
An SVG-based fractal noise texture overlay for subtle paper-like grain.

### Rule Line
```css
.rule-line {
    height: 1px;
    background: linear-gradient(to right, transparent, var(--border) 20%, var(--border) 80%, transparent);
}
```
A fading horizontal rule — fades to transparent at edges.

---

## Theme System

### Dark Mode Implementation
1. **Class-based**: `darkMode: "class"` in Tailwind config
2. **Persistence**: Theme saved in `localStorage` as `tt_theme`
3. **No-flash script**: Inline `<script>` in `<head>` applies `.dark` before first paint
4. **Toggle**: `ThemeToggle` component with Sun/Moon SVG icons
5. **System default**: Falls back to `prefers-color-scheme: dark` if no saved preference

```javascript
// No-flash script (runs before CSS paint)
var t = localStorage.getItem('tt_theme');
if (t === 'dark' || (!t && matchMedia('(prefers-color-scheme: dark)').matches)) {
    document.documentElement.classList.add('dark');
}
```

---

## Subject Colour System

Each timetable subject gets a distinct colour for visual differentiation:

### Pre-assigned Colours (30+ subjects)
```
Maths      → #6366f1 (indigo)     Physics    → #0ea5e9 (sky)
Science    → #10b981 (emerald)    Chemistry  → #14b8a6 (teal)
English    → #f59e0b (amber)      Biology    → #22c55e (green)
Hindi      → #ec4899 (pink)       History    → #f43f5e (rose)
Computer   → #8b5cf6 (violet)     PE/Sports  → #f97316 (orange)
Sanskrit   → #a855f7 (purple)     Music      → #0891b2 (cyan)
```

### Fallback Palette
12-colour rotation for unknown subjects — ensures no two adjacent subjects collide.

---

## Print Styles

```css
@media print {
    .no-print { display: none !important; }
}
```

Header, nav, theme toggle, share links panel — all hidden in print. Timetable grids are the only thing printed.

---

## Layout Structure

```
┌─────────────────────────────────────────────────┐
│  Header (no-print)                              │
│  ┌──────┐  ┌──────────────────────┐  ┌────────┐│
│  │ Logo │  │  Dashboard · Rules ·  │  │ Theme  ││
│  │  tt  │  │  New School          │  │ User   ││
│  └──────┘  └──────────────────────┘  └────────┘│
├─────────────────────────────────────────────────┤
│  Main Content (max-w-7xl, px-6, py-10)          │
│                                                 │
│  ┌─────────────────────────────────────────┐    │
│  │  Page content                           │    │
│  └─────────────────────────────────────────┘    │
│                                                 │
└─────────────────────────────────────────────────┘
```

- Max width: `max-w-7xl` (1280px)
- Padding: `px-6 py-10` (24px horizontal, 40px vertical)
- Header: sticky border-bottom with logo, nav links, theme toggle, user menu

---

## Related

- [[03 - Frontend Deep Dive]] — component implementation
- [[00 - Project Overview]] — project context
- [[PRD]] — feature requirements driving the design
