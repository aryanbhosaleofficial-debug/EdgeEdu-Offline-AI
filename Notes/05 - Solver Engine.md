---
title: Solver Engine
aliases: [CP-SAT, OR-Tools, Constraint Solver]
tags: [solver, ortools, cpsat, constraints, algorithms]
created: 2026-07-26
---

# 🧠 Solver Engine

> Back to [[00 - Project Overview (EdgeEdu)]] · See also [[06 - Services Pipeline]]

**File**: `backend/app/services/solver/engine.py`

The heart of the system. Uses **Google OR-Tools CP-SAT** to place every lecture into a grid without breaking hard rules, then optimises soft preferences.

---

## Model Design

### Decision Variables

For every `(assignment, day, period)` combination:
```
x[(assignment_id, day, period)] = BoolVar
```
Meaning: "this lecture of teacher→subject→section is scheduled in this slot."

Variables are **only created** for feasible slots:
- Teacher must be available on that day+period
- Slot must not be occupied by a `FixedSlot`

---

## Hard Constraints (Never Broken)

| ID | Rule | Implementation |
|----|------|----------------|
| **H1** | Each assignment gets exactly its required lectures | `sum(vars_for_assignment) == lectures` |
| **H2** | A class has at most one lecture per slot | `sum(section_vars_in_slot) <= 1` |
| **H3** | A teacher is in at most one place per slot | `sum(teacher_vars_in_slot) <= 1` |
| **H4** | Teacher availability respected | Variable not created if unavailable |
| **H5** | Fixed slots block the section's cell | Variable not created if slot is fixed |
| **H6** | Lab room capacity per slot | `sum(lab_assignments_in_slot) <= lab_room_count` |

---

## Soft Constraints (Preferences — Minimise Penalty)

All soft constraints contribute weighted penalty terms to: `Minimize(Σ weight × violation_var)`

### Built-in Rules (toggleable)

| Weight | Rule | Description |
|--------|------|-------------|
| **15** | `keep_key_periods_filled` | 1st, 2nd, before/after break, last period should never be free |
| **10** | `teacher_rest_after_two` | Avoid 3 consecutive lectures for any teacher |
| **6** | `spread_subjects` (cluster) | Never 3+ of the same subject in one day |
| **4** | `spread_subjects` (doubles cap) | Limit consecutive double periods to `max_doubles_per_week` |
| **2** | `avoid_back_to_back_free` | No two consecutive free periods for a class |
| **1** | `spread_subjects` (repeat) | Prefer one occurrence of each subject per day |
| **p** | `morning_hard_subjects` | Penalty grows with period index for morning-preference subjects |

### Custom Rules (admin-defined)

| Weight | Type | Description |
|--------|------|-------------|
| **8** | `subject_time` | Subject prefers morning/afternoon |
| **7** | `subject_max_per_day` | Subject at most N times per class per day |
| **6** | `subject_position` | Subject avoids first/last period |

---

## Double Period Handling

The solver **allows** double periods (two consecutive periods of the same subject) but limits them:

1. For each subject in each section, on each day:
   - Create binary indicator `dbl[p]` = subject is in both period `p` AND `p+1`
   - Linearised: `dbl <= sv[p]`, `dbl <= sv[p+1]`, `dbl >= sv[p] + sv[p+1] - 1`
2. Sum all doubles across the week for each section
3. Penalise if sum exceeds `max_doubles_per_week` (default: 2)

---

## Protected Periods

"Key" periods that should stay occupied:

```python
def protected_periods(day):
    # 0-indexed period numbers
    prot = {0}              # 1st period
    prot.add(ndp - 1)       # last period
    prot.add(1)             # 2nd period
    if lunch_after:
        prot.add(lunch_after - 1)  # before break
        prot.add(lunch_after)      # after break
    return prot
```

---

## Half-Day Support

- Each day has its own `periods_in_day[d]` count
- Half-days (e.g. Saturday) have fewer periods: `school.half_day_periods`
- Slot list only includes real `(day, period)` cells — tail periods on half-days are excluded
- Protected period calculation adapts to the day's actual period count

---

## Solver Configuration

```python
solver.parameters.max_time_in_seconds = time_limit  # configurable
solver.parameters.num_search_workers = 8             # parallel threads
```

| Mode | Time Limit |
|------|-----------|
| Background job | 180 seconds |
| Sync endpoint | 25 seconds (Vercel proxy limit) |
| Admin config | `solve_time_limit` (default 30s) |

Actual limit = `min(admin_config, mode_cap)`

---

## Result Status

| Status | Meaning |
|--------|---------|
| `OPTIMAL` | Best possible solution found |
| `FEASIBLE` | Valid solution, may not be optimal (time ran out) |
| `INFEASIBLE` | No valid placement exists |

On `OPTIMAL` or `FEASIBLE`: return placed slots (section, teacher, subject, day, period).
On `INFEASIBLE`: return all assignments as conflicts for diagnostic.

---

## Room Assignment (Post-Solve)

Rooms are **not part of the CP-SAT model**. After solving:

1. Greedy assignment in `generation.py::_assign_rooms()`
2. For each solved cell:
   - Find subject's required room type
   - Pick an available room of that type not already used in this `(day, period)`
   - If no room available → `room_id` stays null

> **Why separate?** Rooms are many-to-many with time slots. Including them in CP-SAT would explode the variable count. Greedy post-processing is sufficient because room counts are rarely the bottleneck.

---

## Performance Notes

- Variable count ≈ `assignments × days × periods` (minus unavailable slots)
- A typical school (30 teachers, 15 sections, 6 days, 8 periods) → ~15,000 variables
- Solver finds FEASIBLE in <5 seconds, OPTIMAL in 15–30 seconds
- 8 parallel search workers utilise modern multi-core CPUs

---

## Related

- [[06 - Services Pipeline]] — how the solver fits in the full pipeline
- [[04 - Database Schema]] — `TimetableSlot` and `FixedSlot` models
- [[02 - Backend Deep Dive]] — services overview
