---
title: Services Pipeline
aliases: [Generation Pipeline, Preflight, Generation]
tags: [services, pipeline, generation, preflight]
created: 2026-07-26
---

# 🔄 Services Pipeline

> Back to [[00 - Project Overview]] · See also [[05 - Solver Engine]]

---

## Generation Pipeline Overview

```
CSV Upload
    ↓
┌─────────────────────────────────────┐
│  csv_import.py                      │
│  Parse → auto-create entities       │
│  → TeacherAssignment rows           │
└─────────────────┬───────────────────┘
                  ↓
           Admin clicks "Generate"
                  ↓
┌─────────────────────────────────────┐
│  generation.py                      │
│                                     │
│  Stage 1: PREFLIGHT                 │
│  ├── Per-section capacity check     │
│  ├── Per-teacher capacity check     │
│  ├── Lab room check                 │
│  └── Protected-period warnings      │
│                                     │
│  Stage 2: SOLVING                   │
│  ├── Build CP-SAT model             │
│  ├── Apply hard constraints         │
│  ├── Apply soft constraints         │
│  └── Solve (time-limited)           │
│                                     │
│  Stage 3: SAVING                    │
│  ├── Greedy room assignment         │
│  ├── Merge fixed slots              │
│  └── Bulk INSERT TimetableSlots     │
└─────────────────────────────────────┘
                  ↓
         Timetable ready to view/export
```

---

## Preflight Check (`preflight.py`)

> "Plain arithmetic catches impossible requests *before* the solver wastes time."

### Checks Performed

#### 1. Basic Sanity
- Working days > 0
- Periods per day > 0
- Sections exist

#### 2. Per-Section Capacity
```
capacity = total_slots - fixed_slots
demand = sum(lectures_per_week for all assignments)

if demand > capacity  →  FAIL
if demand < capacity  →  WARN (free periods will remain)
```

#### 3. Protected Period Coverage
- Key periods: 1st, 2nd, before/after break, last
- If `demand < required_protected_slots` → WARN

#### 4. Per-Teacher Capacity
```
available = slots where teacher is available
if demand > available  →  FAIL

if demand > max_per_day × num_days  →  FAIL
if demand < min_per_week  →  WARN (below contract minimum)
```

#### 5. Lab Room Check
- If lab subjects exist but no lab rooms configured → FAIL

### Output: `PreflightReport`
```python
@dataclass
class PreflightReport:
    feasible: bool          # False if any FAIL
    errors: List[str]       # FAIL messages
    warnings: List[str]     # WARN messages
    stats: Dict             # num_days, periods, sections, teachers, etc.
```

---

## Background Generation (`generation.py`)

### Two Execution Modes

| Mode | Trigger | Time Cap | Purpose |
|------|---------|----------|---------|
| **Sync** | Direct API call | 25s | Legacy, within proxy timeout |
| **Async** | `POST generate-async` | 180s | Background thread, UI polls |

### Async Job Lifecycle

```
QUEUED → RUNNING → SUCCEEDED / FAILED
  │         │
  │         ├── stage: "preflight"
  │         ├── stage: "solving"
  │         └── stage: "saving"
  │
  └── UI polls GET /jobs/{id} every ~2s
```

### Error Handling

- `GenerationError` — expected errors (preflight fail, infeasible)
  - Sets `job.status = "FAILED"`, `job.error = JSON.dumps([messages])`
- `Exception` (catch-all) — unexpected crashes
  - Same handling, with `"Unexpected error: ..."` message
- `finally` block always sets `finished_at` and clears `stage`

### Background Thread
```python
def run_generation_job(job_id: int):
    db = SessionLocal()  # Own session — not the request's
    try:
        job.status = "RUNNING"
        tt = generate_timetable(db, school, name, time_cap_seconds=180, job=job)
        job.timetable_id = tt.id
        job.status = "SUCCEEDED"
    except GenerationError:
        ...  # FAILED
    except Exception:
        ...  # FAILED
    finally:
        job.finished_at = datetime.utcnow()
        db.close()
```

---

## CSV Import (`csv_import.py`)

### Column Mapping

| CSV Header | Internal Name |
|------------|---------------|
| Teacher Name | `teacher_name` |
| Subject | `subject` |
| Standard | `standard` |
| Section | `section` |
| Lectures/Week | `lectures_per_week` |
| Preferred Time | `preferred_time` (optional) |
| Special Room | `special_room` (optional) |

### Behavior

1. **Replace mode** (default): Clear all existing teaching data first
2. **Auto-creation**: Teachers, subjects, standards, sections created if they don't exist
3. **Colour assignment**: 30+ known subjects get fixed colors; unknown subjects cycle through a palette
4. **Room type detection**: "Lab" → LAB, "Library" → LIBRARY, "Hall" → HALL, "Ground/Field" → OTHER
5. **Lecture ranges**: "4-5" → `lectures_per_week=4, lectures_per_week_max=5`

---

## Excel Export (`excel_export.py`)

Generates an `.xlsx` workbook with:
- **One sheet per class** (section): day × period grid
- **One sheet per teacher** (in full-school bundle): day × period grid
- Cells show `Subject\nTeacher` with subject color as background
- Headers: period labels with times
- Uses openpyxl for zero-dependency export

---

## AI Rules Parser (`ai_rules.py`)

Translates plain-English rules into structured `CustomRule` objects:

```
"Keep Maths in the morning"
    ↓ Gemini/Claude API call
{rule_type: "subject_time", subject_name: "Maths", param_text: "morning"}
```

### Supported Rule Types
| Type | Example | Params |
|------|---------|--------|
| `subject_time` | "Science in the afternoon" | `param_text: "afternoon"` |
| `subject_max_per_day` | "PE at most once a day" | `param_int: 1` |
| `subject_position` | "Library never last period" | `param_text: "last"` |

### Provider Support
- **Gemini** (default): `gemini-2.0-flash` via `generativelanguage.googleapis.com`
- **Claude**: `claude-haiku-4-5` via `api.anthropic.com`
- Admin supplies their own API key

---

## Related

- [[05 - Solver Engine]] — CP-SAT model details
- [[04 - Database Schema]] — all models
- [[08 - API Reference]] — endpoint triggers
- [[10 - CSV Format & Import]] — upload details
