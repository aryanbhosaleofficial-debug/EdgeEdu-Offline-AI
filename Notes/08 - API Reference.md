---
title: API Reference
aliases: [Endpoints, REST API]
tags: [api, rest, endpoints]
created: 2026-07-26
---

# 📡 API Reference

> Back to [[00 - Project Overview (EdgeEdu)]] · See also [[02 - Backend Deep Dive]]

Base URL: `/api` (all routes prefixed)

---

## 🔓 Public Endpoints

### Health
| Method | Path | Response |
|--------|------|----------|
| `GET` | `/api/health` | `{"status": "ok", "service": "timetable-ai"}` |

### Auth
| Method | Path | Body | Response |
|--------|------|------|----------|
| `POST` | `/api/auth/register` | `{email, name, password}` | `{access_token, token_type, user}` |
| `POST` | `/api/auth/login` | `{email, password}` | `{access_token, token_type, user}` |
| `GET` | `/api/auth/me` | — | `{id, email, name, role}` |

### Share (Parent View)
| Method | Path | Response |
|--------|------|----------|
| `GET` | `/api/share/{token}` | `{school, timetable, standards, slots}` (sanitised) |

---

## 🔐 Login-Required Endpoints

### Rules (Global Config)
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/rules` | Get current rule config |
| `PUT` | `/api/rules` | Update rule toggles/settings |

### Custom Rules
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/rules/custom` | List all custom rules |
| `POST` | `/api/rules/custom` | Create a custom rule |
| `PATCH` | `/api/rules/custom/{id}` | Toggle enable/disable |
| `DELETE` | `/api/rules/custom/{id}` | Delete a custom rule |
| `POST` | `/api/rules/custom/parse` | NLP: `{text}` → structured rule |

---

## 🏫 School-Scoped Endpoints

> All require `Authorization: Bearer {token}` + user must own the school.

### Schools
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/schools` | List user's schools |
| `GET` | `/api/schools/{sid}` | Get school details |
| `POST` | `/api/schools` | Create new school |

### Subjects
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/schools/{sid}/subjects` | List subjects |
| `POST` | `/api/schools/{sid}/subjects` | Create subject |
| `PATCH` | `/api/schools/{sid}/subjects/{id}` | Update subject |
| `DELETE` | `/api/schools/{sid}/subjects/{id}` | Delete subject |

### Standards
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/schools/{sid}/standards` | List standards (with sections) |
| `POST` | `/api/schools/{sid}/standards` | Create standard |

### Rooms
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/schools/{sid}/rooms` | List rooms |
| `POST` | `/api/schools/{sid}/rooms` | Create room |
| `PATCH` | `/api/schools/{sid}/rooms/{id}` | Update room |
| `DELETE` | `/api/schools/{sid}/rooms/{id}` | Delete room |

### Teachers & Assignments
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/schools/{sid}/teachers` | List teachers |
| `GET` | `/api/schools/{sid}/assignments` | List all assignments |
| `POST` | `/api/schools/{sid}/assignments` | Create assignment |

### CSV Import
| Method | Path | Content-Type | Description |
|--------|------|-------------|-------------|
| `POST` | `/api/schools/{sid}/import` | `multipart/form-data` | Upload CSV/Excel → auto-create entities |

### Fixed Slots
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/schools/{sid}/fixed-slots` | List fixed slots |
| `POST` | `/api/schools/{sid}/fixed-slots` | Create fixed slot |
| `DELETE` | `/api/schools/{sid}/fixed-slots/{id}` | Delete fixed slot |

### Timetables
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/schools/{sid}/timetables` | List timetables |
| `GET` | `/api/schools/{sid}/timetables/{tid}` | Get timetable (with slots) |
| `POST` | `/api/schools/{sid}/timetables/preflight` | Run preflight only |
| `POST` | `/api/schools/{sid}/timetables/generate-async` | Start background job |
| `GET` | `/api/schools/{sid}/timetables/jobs/{jid}` | Poll job status |
| `POST` | `/api/schools/{sid}/timetables/{tid}/publish` | Publish timetable |
| `DELETE` | `/api/schools/{sid}/timetables/{tid}` | Delete timetable |
| `GET` | `/api/schools/{sid}/timetables/{tid}/export.xlsx` | Download Excel |

### Share Links
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/schools/{sid}/timetables/{tid}/share-links` | List share links |
| `POST` | `/api/schools/{sid}/timetables/{tid}/share-links` | Create share link |
| `DELETE` | `/api/schools/{sid}/timetables/{tid}/share-links/{lid}` | Revoke share link |

---

## Error Responses

```json
{
  "detail": "Error message string or object"
}
```

| Status | Meaning |
|--------|---------|
| 400 | Bad request / validation error |
| 401 | Not authenticated / expired token |
| 403 | Forbidden (wrong school owner) |
| 404 | Resource not found |
| 422 | Validation error (Pydantic) |

---

## Interactive Docs

FastAPI auto-generates:
- **Swagger UI**: `http://localhost:8000/docs`
- **ReDoc**: `http://localhost:8000/redoc`

---

## Related

- [[02 - Backend Deep Dive]] — route files
- [[07 - Auth & Security]] — auth flow
- [[03 - Frontend Deep Dive]] — API client consuming these
