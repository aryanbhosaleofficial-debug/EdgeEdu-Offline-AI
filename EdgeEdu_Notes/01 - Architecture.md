---
title: Architecture
aliases: [System Architecture, Tech Stack]
tags: [architecture, fullstack]
created: 2026-07-26
---

# 🏗️ Architecture

> Back to [[00 - Project Overview]]

---

## Tech Stack

| Layer | Choice |
|-------|--------|
| **Frontend** | Next.js 14 (App Router) + Tailwind CSS 3 + SWR |
| **Backend** | FastAPI + SQLAlchemy |
| **Solver** | Google OR-Tools (CP-SAT) |
| **Database** | SQLite locally / PostgreSQL in production |
| **Auth** | scrypt + HS256 bearer tokens (stdlib only) |
| **Export** | openpyxl (.xlsx) |
| **AI (optional)** | Gemini 2.0 Flash / Claude Haiku for NLP rule parsing |

---

## High-Level Data Flow

```
┌──────────────┐    CSV/Excel    ┌──────────────────┐
│   Admin UI   │ ──────────────► │  csv_import.py   │
│  (Next.js)   │                 │  auto-creates:   │
│              │                 │  teachers,       │
│              │                 │  subjects,       │
│              │                 │  standards,      │
│              │                 │  sections        │
└──────┬───────┘                 └────────┬─────────┘
       │ SWR / fetch                      │
       ▼                                  ▼
┌──────────────┐                 ┌──────────────────┐
│  FastAPI     │ ───────────────►│  Database (ORM)  │
│  Backend     │                 │  SQLite / PG     │
└──────┬───────┘                 └────────┬─────────┘
       │                                  │
       ▼                                  │
┌──────────────┐                          │
│  Generation  │◄─────────────────────────┘
│  Pipeline    │
│  ┌─────────┐ │
│  │Preflight│ │  arithmetic feasibility check
│  └────┬────┘ │
│       ▼      │
│  ┌─────────┐ │
│  │ Solver  │ │  OR-Tools CP-SAT model
│  │ Engine  │ │  hard + soft constraints
│  └────┬────┘ │
│       ▼      │
│  ┌─────────┐ │
│  │  Room   │ │  greedy room assignment
│  │ Assign  │ │
│  └────┬────┘ │
│       ▼      │
│  ┌─────────┐ │
│  │  Save   │ │  bulk INSERT → TimetableSlot
│  └─────────┘ │
└──────────────┘
```

---

## Project Layout

```
AI-TimeTable/
├── backend/
│   ├── app/
│   │   ├── api/
│   │   │   ├── deps.py          # auth + school-ownership dependencies
│   │   │   └── routes/          # auth, share, school, academic,
│   │   │                        # teacher, timetable, rule, fixed_slot
│   │   ├── core/
│   │   │   ├── config.py        # Pydantic Settings (env vars)
│   │   │   ├── database.py      # SQLAlchemy engine + session
│   │   │   ├── migrate.py       # startup column migrations
│   │   │   └── security.py      # scrypt + HS256 JWT
│   │   ├── models/              # SQLAlchemy ORM models
│   │   ├── schemas/             # Pydantic request/response
│   │   └── services/
│   │       ├── csv_import.py    # CSV/Excel → DB
│   │       ├── preflight.py     # feasibility math
│   │       ├── generation.py    # pipeline + background runner
│   │       ├── excel_export.py  # .xlsx builder
│   │       ├── ai_rules.py      # NLP → structured rule (Gemini/Claude)
│   │       └── solver/
│   │           └── engine.py    # OR-Tools CP-SAT model
│   ├── test_api_smoke.py
│   └── test_solver_standalone.py
├── frontend/
│   ├── src/
│   │   ├── app/                 # Next.js App Router pages
│   │   ├── components/          # UI primitives, UserMenu, ShareLinks
│   │   └── lib/
│   │       ├── api.ts           # auth-aware API client
│   │       └── types.ts         # TypeScript interfaces
│   ├── next.config.mjs          # /api proxy rewrite
│   └── tailwind.config.ts
├── render.yaml                  # Render deploy blueprint
├── DEPLOY.md
└── README.md
```

---

## Communication Pattern

- **Local dev**: Next.js rewrites `/api/*` → `http://localhost:8000/api/*`
- **Production**: Browser calls `NEXT_PUBLIC_BACKEND_URL` directly (bypasses Vercel proxy to avoid timeout on cold starts)
- **Auth**: Bearer token in `Authorization` header (stored in `localStorage`)
- **Data fetching**: SWR for reads, imperative `fetch` for mutations
- **CORS**: `allow_origins=["*"]`, `allow_credentials=False` (safe since auth is bearer-only, no cookies)

---

## Related

- [[02 - Backend Deep Dive]] — FastAPI server details
- [[03 - Frontend Deep Dive]] — Next.js structure
- [[05 - Solver Engine]] — the heart of the system
