---
title: Frontend Deep Dive
aliases: [Frontend, Next.js Frontend]
tags: [frontend, nextjs, react, tailwind]
created: 2026-07-26
---

# 🎨 Frontend Deep Dive

> Back to [[00 - Project Overview (EdgeEdu)]] · See also [[01 - Architecture]]

---

## Stack

| Tech | Version | Role |
|------|---------|------|
| Next.js | 14.2.5 | App Router, SSR/CSR |
| React | 18.3.1 | UI components |
| Tailwind CSS | 3.4.6 | Styling |
| SWR | 2.2.5 | Data fetching + caching |
| TypeScript | 5.5.3 | Type safety |
| qrcode | 1.5.4 | QR code generation for share links |

---

## Pages (App Router)

```
src/app/
├── globals.css          # Design tokens, dark mode, typography
├── layout.tsx           # Root layout: header, nav, theme toggle, user menu
├── page.tsx             # Dashboard — lists all schools
├── login/               # Auth: login / register
├── setup/               # New School wizard
├── school/              # School detail: timetable, teachers, etc.
│   └── [id]/            # Dynamic route per school
├── rules/               # Global scheduling rules config
└── share/               # Public parent view (token-based)
    └── [token]/
```

---

## Key Components

| Component | File | Purpose |
|-----------|------|---------|
| `ThemeToggle` | `ThemeToggle.tsx` | Dark/light mode switcher (persists in `localStorage`) |
| `UserMenu` | `UserMenu.tsx` | Shows logged-in user, logout button |
| `ShareLinks` | `ShareLinks.tsx` | Create/revoke share links with QR codes |
| `ui/` | Various | Reusable UI primitives: `Card`, `Button`, `Badge` |

---

## Design System

- **Fonts**: Instrument Serif (display), Work Sans (body), JetBrains Mono (monospace)
- **Theme**: Light + Dark mode with `localStorage` persistence
- **No-flash script**: Applies saved theme before first paint (inline `<script>` in `layout.tsx`)
- **Color palette**: Curated subject colors in `csv_import.py` — 30+ known subjects have fixed hex colors

---

## API Client (`lib/api.ts`)

### Base URL Logic
```
Local dev  →  /api  (Next.js rewrites to localhost:8000)
Production →  NEXT_PUBLIC_BACKEND_URL  (direct to Render backend)
```

### Auth Token
- Stored in `localStorage` as `tt_token`
- Attached as `Authorization: Bearer {token}` to every request
- Auto-redirect to `/login` on 401

### Error Handling
- Custom `ApiError` class with `status` and `detail`
- Response body read once as text, then parsed as JSON (avoids "body stream already read")
- 401 → clear token → redirect to login

### Core Functions
```typescript
fetcher<T>(path)         // SWR-compatible GET
req<T>(path, opts?)      // Generic authed request
api.register({...})      // POST /auth/register
api.login({...})         // POST /auth/login
api.importCsv(sid, file) // Multipart file upload
api.generateAsync(sid, body) // Start background job
api.getJob(sid, jobId)   // Poll job status
api.downloadExport(...)  // Blob download → auto-click <a>
api.createShareLink(...) // Mint share token
api.parseRule(text)      // NLP → structured rule
```

---

## TypeScript Interfaces (`lib/types.ts`)

All major types are defined:

| Interface | Fields |
|-----------|--------|
| `School` | id, name, board, periods_per_day, half_day_periods, academic_year, working_days, periods, breaks |
| `Subject` | id, name, code, requires_room_type, color |
| `Teacher` | id, name, email, max_periods_per_day, min_periods_per_week |
| `Assignment` | teacher_id, subject_id, section_id, lectures_per_week, preferred_time |
| `TimetableSlot` | section_id, teacher_id, subject_id, room_id, day_index, period_index, is_free, is_fixed, conflict + display names |
| `Timetable` | id, name, status (DRAFT/GENERATING/GENERATED/FAILED/APPROVED/PUBLISHED), slots |
| `GenerationJob` | status (QUEUED/RUNNING/SUCCEEDED/FAILED), stage (preflight/solving/saving) |
| `ShareLink` | token, timetable_id, revoked |
| `ShareView` | school, timetable, standards, slots (parent-safe — no staff details) |
| `RuleConfig` | keep_key_periods_filled, teacher_rest_after_two, spread_subjects, etc. |
| `CustomRule` | rule_type, subject_name, param_text, param_int, enabled |

---

## `next.config.mjs`

```javascript
// Proxy /api/* to the backend during local development
async rewrites() {
  return [{
    source: "/api/:path*",
    destination: `${process.env.BACKEND_URL || "http://localhost:8000"}/api/:path*`,
  }];
}
```

---

## Related

- [[07 - Auth & Security]] — token flow
- [[08 - API Reference]] — all endpoints
- [[01 - Architecture]] — communication pattern
