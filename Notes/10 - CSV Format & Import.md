---
title: CSV Format & Import
aliases: [CSV, Import, Upload, Data Input]
tags: [csv, import, data, upload]
created: 2026-07-26
---

# 📄 CSV Format & Import

> Back to [[00 - Project Overview (EdgeEdu)]] · See also [[06 - Services Pipeline]]

---

## CSV Column Format

| Column | Required | Example | Notes |
|--------|----------|---------|-------|
| **Teacher Name** | ✅ | Mr. Sharma | Same teacher may appear on many rows |
| **Subject** | ✅ | Maths | Auto-created if new |
| **Standard** | ✅ | 10th | Grade / year level |
| **Section** | ✅ | A | Each section is a separate class |
| **Lectures/Week** | ✅ | 6 (or 4-5) | Fixed number or a range |
| **Preferred Time** | ❌ | Morning | Soft preference for solver |
| **Special Room** | ❌ | Lab | Links subject to a room type |

> Template: `frontend/public/template.csv`

---

## Column Name Mapping

Headers are normalised case-insensitively:

```python
EXPECTED_COLUMNS = {
    "teacher name": "teacher_name",
    "subject": "subject",
    "standard": "standard",
    "section": "section",
    "lectures/week": "lectures_per_week",
    "preferred time": "preferred_time",
    "special room": "special_room",
}
```

---

## Import Behaviour

### Replace Mode (default)

When `replace=True`, the school's existing data is **completely cleared** before import:

1. Delete `TimetableSlot` → `Timetable` → `FixedSlot`
2. Delete `TeacherAssignment` → `Section` → `Standard`
3. Delete `Subject` → `Teacher`
4. Then import fresh

> [!WARNING]
> Re-upload **replaces** everything. Old timetables are deleted.

### Auto-Creation

Each CSV row triggers "get or create" for:
- **Teacher** — matched by name (case-insensitive)
- **Subject** — matched by name; `Special Room` sets `requires_room_type`
- **Standard** — matched by name
- **Section** — matched by (standard_id, name)

### Lecture Ranges

```
"6"   → lectures_per_week=6, lectures_per_week_max=None
"4-5" → lectures_per_week=4, lectures_per_week_max=5
```

The solver uses the minimum (`lectures_per_week`) as the exact requirement.

---

## Special Room Mapping

| CSV Value | Room Type |
|-----------|-----------|
| (empty) | `CLASSROOM` |
| Lab, lab, laboratory | `LAB` |
| Library | `LIBRARY` |
| Hall, Auditorium | `HALL` |
| Ground, Field, Playground | `OTHER` |

---

## Subject Colour Assignment

### Known Subjects (fixed colours)
```
Maths       → #6366f1 (indigo)
Science     → #10b981 (emerald)
English     → #f59e0b (amber)
Hindi       → #ec4899 (pink)
Physics     → #0ea5e9 (sky)
Chemistry   → #14b8a6 (teal)
Biology     → #22c55e (green)
History     → #f43f5e (rose)
Computer    → #8b5cf6 (violet)
PE/Sports   → #f97316 (orange)
Art         → #e11d48 (red)
Music       → #0891b2 (cyan)
```
…and 15+ more subjects have pre-assigned colors.

### Unknown Subjects
Cycle through a palette:
```python
PALETTE = ["#6366f1", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899",
           "#0ea5e9", "#84cc16", "#f97316", "#14b8a6", "#a855f7", "#06b6d4"]
```

Partial matching: "Maths 1" starts with "maths" → gets maths color.

---

## File Format Support

| Format | Detection |
|--------|-----------|
| `.csv` | Default — `pd.read_csv()` |
| `.xlsx` | `pd.read_excel()` via openpyxl |
| `.xls` | `pd.read_excel()` via openpyxl |

---

## Error Handling

- Missing required columns → `ValueError` with column names
- Invalid row data → skipped with warning: `"Row {n}: skipped ({reason})"`
- Returns: `(rows_imported: int, warnings: List[str])`

---

## Example CSV

```csv
Teacher Name,Subject,Standard,Section,Lectures/Week,Preferred Time,Special Room
Mr. Sharma,Maths,10th,A,6,Morning,
Mrs. Gupta,English,10th,A,5,,
Dr. Patil,Science,10th,A,4,Morning,Lab
Mr. Singh,Hindi,10th,A,4,,
Ms. Rao,Social Studies,10th,A,3,,
Mr. Kumar,Computer,10th,A,2,,Lab
Mrs. Joshi,PE,10th,A,2,Afternoon,Ground
```

---

## Related

- [[06 - Services Pipeline]] — full import flow
- [[04 - Database Schema]] — entities created by import
- [[05 - Solver Engine]] — how assignments feed constraints
