---
title: Auth & Security
aliases: [Authentication, Security, JWT]
tags: [auth, security, jwt, scrypt]
created: 2026-07-26
---

# 🔐 Auth & Security

> Back to [[00 - Project Overview (EdgeEdu)]] · See also [[02 - Backend Deep Dive]]

---

## Overview

The auth system is built **entirely with Python's standard library** — no `passlib`, `python-jose`, or `PyJWT`. This avoids:
- passlib's Python 3.13+ breakage
- Extra dependencies on Render deploys
- Supply chain risk

---

## Password Hashing — scrypt

```python
# Parameters
_SCRYPT_N = 2^14    # CPU/memory cost
_SCRYPT_R = 8       # block size
_SCRYPT_P = 1       # parallelism

# Storage format
"scrypt${base64_salt}${base64_key}"
```

### Hash Flow
```
password → scrypt(password, random_16_byte_salt) → "scrypt$salt$key"
```

### Verify Flow
```
password + stored_hash → split("$") → scrypt(password, stored_salt)
    → hmac.compare_digest(computed_key, stored_key)
```

> `hmac.compare_digest` prevents timing attacks.

---

## Token System — HS256 JWT (manual)

### Token Structure
```
{header}.{payload}.{signature}

header  = base64({"alg": "HS256", "typ": "JWT"})
payload = base64({"sub": user_id, "exp": unix_timestamp})
sig     = base64(HMAC-SHA256(SECRET_KEY, header.payload))
```

### Create Token
```python
def create_access_token(user_id, expires_minutes=1440):  # 24h default
    header = b64({"alg": "HS256", "typ": "JWT"})
    payload = b64({"sub": str(user_id), "exp": now + 60 * expires_minutes})
    return f"{header}.{payload}.{sign(header.payload)}"
```

### Decode Token
```python
def decode_access_token(token) -> Optional[int]:
    header, payload, sig = token.split(".")
    if not hmac.compare_digest(sig, sign(f"{header}.{payload}")):
        return None  # invalid signature
    claims = json.loads(unb64(payload))
    if claims["exp"] < time.time():
        return None  # expired
    return int(claims["sub"])  # user_id
```

---

## Auth Flow

```
┌──────────┐         ┌──────────┐         ┌──────────┐
│  Client   │ ──────► │ POST     │ ──────► │  DB      │
│           │         │ /auth/   │         │          │
│           │         │ register │         │ User     │
│           │ ◄────── │ or login │ ◄────── │ lookup   │
│           │  token  │          │  verify │          │
└──────┬────┘         └──────────┘         └──────────┘
       │
       │  Authorization: Bearer {token}
       ▼
┌──────────┐
│ Protected│
│ endpoint │  → get_current_user() → decode_access_token()
│          │  → require_school_access() → verify ownership
└──────────┘
```

---

## Ownership Model

### School Ownership
```python
def require_school_access(school_id, user, db):
    school = db.query(School).filter(School.id == school_id).first()
    if not school:
        raise 404
    # Legacy schools (owner_id=NULL) accessible by any logged-in admin
    if school.owner_id is not None and school.owner_id != user.id:
        raise 403
```

### Route Protection Levels

| Level | Routes | Check |
|-------|--------|-------|
| **Public** | `/auth/*`, `/share/{token}`, `/health` | None |
| **Login** | `/rules/*` | `get_current_user()` |
| **School-scoped** | `/schools/{sid}/*` | `require_school_access()` |

---

## Client-Side Auth

### Token Storage
```typescript
localStorage.setItem("tt_token", token);   // on login
localStorage.removeItem("tt_token");        // on logout
```

### Request Headers
```typescript
function authHeaders() {
    const token = localStorage.getItem("tt_token");
    return token ? { Authorization: `Bearer ${token}` } : {};
}
```

### 401 Handling
```typescript
function handleUnauthorized(path) {
    clearToken();
    if (!window.location.pathname.startsWith("/login")) {
        window.location.href = "/login";
    }
}
```

---

## Parent Share Links

A separate, cookie-less access mechanism for parents:

1. Admin creates share link → generates random token
2. Token stored in `ShareLink` table with `timetable_id`
3. Parent accesses `/share/{token}` → public endpoint returns sanitised view
4. **No staff details** exposed — `ParentSlot` omits `teacher_id`, `teacher_name`, `room_id`
5. Admin can **revoke** link instantly → `revoked = True` → 404 on next access

---

## Security Considerations

| Concern | Mitigation |
|---------|------------|
| Token theft | 24h expiry, can rotate `SECRET_KEY` to invalidate all |
| Timing attacks | `hmac.compare_digest` for all comparisons |
| CORS | `allow_origins=["*"]` is safe — auth is bearer-only, no cookies |
| Default `SECRET_KEY` | Render auto-generates via `render.yaml`. Local dev uses dummy |
| Password storage | scrypt with random salt, industry-standard |
| SQL injection | SQLAlchemy ORM (parameterised queries) |

---

## Related

- [[02 - Backend Deep Dive]] — `deps.py` dependencies
- [[03 - Frontend Deep Dive]] — API client token handling
- [[08 - API Reference]] — auth endpoints
