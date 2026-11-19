---
title: AI-TimeTable — Project Overview
aliases: [Timetable AI, AI TimeTable, MOC]
tags: [project, overview, moc]
created: 2026-07-26
version: v2.1
---

# 🗓️ AI-TimeTable

> AI-powered, conflict-free timetable generator for schools, colleges, and universities.

Admins feed teacher data once; the engine generates valid timetables for **every class and every teacher in one pass**.

---

## 🗂️ Map of Content

| Note | Description |
|------|-------------|
| [[PRD]] | Product requirements, features, roadmap |
| [[Design]] | Design system — palette, typography, components |
| [[Modules & Phases]] | Module breakdown, implementation phases |
| [[Architecture]] | Detailed architecture, ADRs, sequence diagrams |
| [[Rules]] | Hard/soft constraints, custom rules, AI parsing |
| [[01 - Architecture]] | Full-stack architecture & data flow |
| [[02 - Backend Deep Dive]] | FastAPI, models, services, solver |
| [[03 - Frontend Deep Dive]] | Next.js 14, pages, components, API client |
| [[04 - Database Schema]] | All SQLAlchemy models & relationships |
| [[05 - Solver Engine]] | OR-Tools CP-SAT — hard/soft constraints |
| [[06 - Services Pipeline]] | Preflight → Generation → Export |
| [[07 - Auth & Security]] | scrypt + HS256, ownership model |
| [[08 - API Reference]] | All REST endpoints |
| [[09 - Deployment Guide]] | Vercel + Render + Supabase |
| [[10 - CSV Format & Import]] | Upload format, column mapping, auto-creation |

---

## ⚡ Quick Start

### Backend
```bash
cd backend
python -m venv .venv && . .venv/Scripts/activate   # Windows
pip install -r requirements.txt
python -m uvicorn app.main:app --reload            # http://localhost:8000
```

### Frontend
```bash
cd frontend
npm install
npm run dev                                        # http://localhost:3000
```

Open http://localhost:3000 → create an account → **New School** → upload CSV → **Generate timetable**.

---

## 🧪 Verification

```bash
cd backend
python test_api_smoke.py       # 27 checks: auth, ownership, CSV, jobs, clashes, share, Excel
python test_solver_standalone.py   # 60 lectures, 0 teacher clashes, 0 class clashes
```

---

## 🗺️ Roadmap

- Full role matrix with approval chains
- Per-school rules config
- Email verification / password reset
- **Out of scope**: exam timetable / invigilation
