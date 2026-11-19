---
title: Backend Deep Dive
aliases: [Backend, FastAPI Backend]
tags: [backend, fastapi, python]
created: 2026-07-26
---

# ⚙️ Backend Deep Dive

> Back to [[00 - Project Overview]] · See also [[01 - Architecture]]

---

## Entry Point — `main.py`

- **FastAPI v2.1.0** (`app = FastAPI(title="Timetable AI", version="2.1.0")`)
- CORS: permissive (`allow_origins=["*"]`), no cookies, bearer-token auth
- On boot:
  1. `Base.metadata.create_all()` — creates missing **tables**
  2. `run_startup_migrations()` — adds missing **columns** to existing tables
- Router mounting:
  - **Public**: `auth`, `share`
  - **School-scoped** (login + ownership): `school`, `academic`, `teacher`, `timetable`, `fixed_slot`
  - **Admin-global**: `rule` (login only, no school scope)

---

## Core Module

### `config.py`
```python
class Settings(BaseSettings):
    DATABASE_URL: str = "sqlite:///./timetable.db"
    SECRET_KEY: str = "change-me-in-production"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 1440  # 24h
```
- Reads from `.env` file
- SQLite by default → zero local setup
- `SECRET_KEY` must be changed in production

### `database.py`
- SQLAlchemy `create_engine` with `check_same_thread=False` for SQLite
- `SessionLocal` factory, `get_db()` dependency generator

### `migrate.py`
> **Key insight**: `create_all()` adds tables but never columns. This migration diffs models vs live schema on every boot and `ALTER TABLE ADD COLUMN` for anything missing.

### `security.py` → See [[07 - Auth & Security]]

---

## API Routes

| File | Prefix | Auth | Description |
|------|--------|------|-------------|
| `auth.py` | `/api/auth` | Public | Register, login, `/me` |
| `share.py` | `/api/share` | Public | `GET /share/{token}` — parent view |
| `school.py` | `/api/schools` | Login + ownership | CRUD schools |
| `academic.py` | `/api/schools/{sid}` | School-scoped | Subjects, standards, rooms |
| `teacher.py` | `/api/schools/{sid}` | School-scoped | Teachers, assignments, CSV import |
| `timetable.py` | `/api/schools/{sid}` | School-scoped | Preflight, generate, list, export, publish |
| `fixed_slot.py` | `/api/schools/{sid}` | School-scoped | Fixed slots (assembly, library) |
| `rule.py` | `/api/rules` | Login only | Global rule config + custom rules + AI parse |

### Dependencies (`deps.py`)

```python
def get_current_user(...)    # Decode bearer token → User
def require_school_access(...)  # User owns the school (or school is legacy/unowned)
```

---

## Services

| Service | File | Purpose |
|---------|------|---------|
| **CSV Import** | `csv_import.py` | Parse CSV/Excel → auto-create teachers, subjects, standards, sections |
| **Preflight** | `preflight.py` | Arithmetic feasibility checks before solving |
| **Generation** | `generation.py` | Full pipeline: preflight → solve → room assign → save |
| **Solver** | `solver/engine.py` | OR-Tools CP-SAT constraint model |
| **Excel Export** | `excel_export.py` | Build `.xlsx` with colour-coded class + teacher sheets |
| **AI Rules** | `ai_rules.py` | Parse plain-English rules via Gemini/Claude |

---

## Key Dependencies

```
fastapi==0.111.0        # Web framework
uvicorn==0.29.0         # ASGI server
sqlalchemy==2.0.30      # ORM
ortools==9.10.4067      # Constraint solver
pandas==2.2.2           # CSV/Excel parsing
openpyxl==3.1.2         # Excel export
httpx==0.27.0           # HTTP client for AI APIs
pydantic==2.7.1         # Data validation
pydantic-settings==2.2.1
psycopg2-binary==2.9.9  # PostgreSQL driver
python-multipart==0.0.9 # File uploads
```

---

## Related

- [[04 - Database Schema]] — all models in detail
- [[05 - Solver Engine]] — CP-SAT constraints
- [[06 - Services Pipeline]] — generation flow
- [[08 - API Reference]] — endpoint catalogue
