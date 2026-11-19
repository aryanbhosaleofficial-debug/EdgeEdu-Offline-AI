---
title: Product Requirements Document
aliases: [PRD, Requirements, Product Spec]
tags: [prd, requirements, product, planning]
created: 2026-07-26
version: v2.1
---

# 📋 Product Requirements Document (PRD)

> Back to [[00 - Project Overview]]

---

## 1. Problem Statement

Schools, colleges, and universities spend **weeks** manually building timetables every academic year. The constraints are brutal:
- A teacher can't be in two rooms at once
- A class can't have two subjects at once
- Lab subjects need lab rooms (finite capacity)
- Balanced distribution across the week is desired
- "Key" periods (first, last, around breaks) shouldn't be empty
- Double periods are sometimes wanted, but not too many

Manual approaches are error-prone, conflict-ridden, and impossible to optimise. Commercial software is expensive and often overkill for single institutions.

---

## 2. Vision

> **One CSV upload → one click → conflict-free timetables for every class and every teacher, ready to share with parents.**

An admin feeds teacher assignment data once. The AI engine generates a valid, optimised timetable in seconds. The result is viewable per class, per teacher, exportable to Excel, and shareable via QR-coded links.

---

## 3. Target Users

| User | Role | Access |
|------|------|--------|
| **School Admin** | Full access — creates school, uploads data, generates timetable, configures rules, shares with parents | Email + password login |
| **Parent** | Read-only class timetable view — no staff details visible | Public share link (token-based, revocable) |
| **Teacher** *(future)* | View own timetable | Planned for v3 |

---

## 4. Core Features (v2.1 — Current Release)

### 4.1 School Setup
- Create a school with board (CBSE/ICSE/STATE/IB/Custom), academic year, periods/day
- Configure working days (Mon–Sat, with half-day support)
- Define period timings and breaks
- Upload CSV/Excel of teacher assignments → auto-creates teachers, subjects, standards, sections

### 4.2 Timetable Generation
- **Pre-flight check** — catches impossible requests before the solver runs
  - Per-section capacity overflow
  - Per-teacher capacity overflow
  - Lab room availability
  - Key period coverage warnings
- **Background generation** — solver runs in a thread; UI polls for progress
  - Stages: `preflight` → `solving` → `saving`
  - Time budget: up to 180 seconds for background jobs
- **Hard constraints** (never broken):
  - No teacher double-booking
  - No class double-booking
  - Lab room capacity per slot
  - Teacher availability respected
  - Fixed slots (assembly, library) preserved
- **Soft constraints** (optimised):
  - Key periods filled
  - Teacher rest after 2 consecutive lectures
  - Subject spread across the week
  - Morning preference for hard subjects
  - Configurable double-period cap
  - Custom rules via natural language (AI-parsed)

### 4.3 Rules Engine
- Admin-toggleable built-in rules (6 toggles)
- Configurable solver time limit
- Custom rules typed in plain English → parsed by Gemini/Claude
  - `subject_time`: "Keep Maths in the morning"
  - `subject_max_per_day`: "PE at most once a day"
  - `subject_position`: "Library never last period"
- Admin supplies their own LLM API key (Gemini or Claude)

### 4.4 Timetable Views
- **Class view** — day × period grid, colour-coded by subject
- **Teacher view** — day × period grid showing classes taught
- **Parent view** — sanitised (no staff details), via share link
- **Excel export** — one sheet per class + one per teacher, colour-coded

### 4.5 Sharing & Publishing
- Generate revocable share links with QR codes
- Parent accesses `/share/{token}` — read-only, no login
- Admin can revoke any link instantly
- Timetable lifecycle: DRAFT → GENERATING → GENERATED → APPROVED → PUBLISHED

### 4.6 Authentication & Multi-tenancy
- Email + password registration/login
- scrypt password hashing + HS256 JWT tokens (stdlib only)
- Each school owned by its creator
- Legacy schools (pre-auth) accessible by any admin
- 24-hour token expiry

### 4.7 Fixed Slots
- Admin locks specific day+period+section cells (e.g. Assembly, Library)
- Solver skips these — never overwritten
- Appear in the timetable as "fixed" entries

---

## 5. Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| **Cold start** | Survive free-tier cold starts (~50s) via background jobs + polling |
| **Solver performance** | FEASIBLE in <5s, OPTIMAL in <30s for typical schools |
| **Zero-setup local** | SQLite default, no Docker, no external DB needed |
| **Auto-migration** | Missing tables/columns added on boot |
| **Free-tier deploy** | Vercel (frontend) + Render (backend) + Supabase (DB) |
| **Print-ready** | `.no-print` class hides nav; timetable grids are print-friendly |
| **Dark mode** | Full light/dark theme with no-flash on load |

---

## 6. Data Flow

```
Admin creates school
    → configures days, periods, breaks
    → uploads CSV of teacher assignments
    → (optional) adds fixed slots, custom rules
    → clicks "Generate"
        → preflight check
        → CP-SAT solver
        → room assignment
        → bulk save
    → views timetable (class/teacher tabs)
    → exports Excel
    → creates share links for parents
    → publishes timetable
```

---

## 7. Roadmap

### v3.0 — Role Matrix & Granular Access
- [ ] Teacher accounts with own timetable view
- [ ] Approval chains (teacher → HOD → principal → publish)
- [ ] Per-school rule config (currently global)
- [ ] Email verification + password reset

### v3.1 — Advanced Scheduling
- [ ] Multi-teacher subjects (team teaching)
- [ ] Substitute teacher suggestions
- [ ] Room-capacity-aware placement (not just type)
- [ ] Historical analytics (teacher load over terms)

### Out of Scope
- ❌ Exam timetable / invigilation scheduling
- ❌ Student-level schedules (elective routing)
- ❌ Real-time attendance tracking

---

## 8. Success Metrics

| Metric | Target |
|--------|--------|
| Zero hard-constraint violations | 100% (solver guarantee) |
| Generation success rate | >95% (with good input data) |
| Time to first timetable | <5 minutes (upload CSV → view result) |
| Excel export accuracy | 1:1 match with on-screen timetable |
| Share link → parent access | <2 seconds load time |

---

## Related

- [[00 - Project Overview]] — quick start
- [[05 - Solver Engine]] — constraint details
- [[11 - Modules & Phases]] — implementation breakdown
- [[12 - Rules]] — rule system deep dive
