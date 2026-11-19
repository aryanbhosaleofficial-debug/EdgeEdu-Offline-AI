"""Curriculum document schema and validation.

Two document shapes are valid; both are one JSON file per
(standard, subject, language) and both load on the Android side:

v3.0 (flat, pipeline-generated):

    {
      "metadata": {
        "file_name": str, "standard": 9|10, "subject": str,
        "language": "English"|"Hindi"|"Marathi", "board": str,
        "schema_version": "3.0", "content_version": int >= 1,
        "last_updated": "YYYY-MM-DD"
      },
      "content_chunks": [{
        "chunk_id": str (unique), "heading": str, "keywords": [str],
        "text": str, "difficulty": 1..5, "importance": 1..5,
        "linked_concepts": [str], "prerequisites": [str],
        # optional, Math only:
        "latex": str,
        "solution_steps": [{"text": str, "latex": str, "verified": bool}]
      }],
      "generation_status": "complete"|"partial"
    }

v3.1 (chapter-nested, the shape the shipped curriculum uses): chunks are
grouped under "chapters" and carry the body in "explanation" (plus richer
optional fields — key_points, formulas, worked_example, …); metadata keeps
the same core identity fields but versions with "version" instead of
"schema_version"/"content_version":

    {
      "metadata": { "file_name", "standard", "subject", "language",
                    "board", "version", "last_updated", … },
      "chapters": [{
        "chapter_id": int, "chapter_title": str,
        "content_chunks": [{ "chunk_id", "heading", "explanation",
                             "difficulty", "importance", … }]
      }]
    }
"""
from __future__ import annotations

import re

SCHEMA_VERSION = "3.0"
LANGUAGES = {"English", "Hindi", "Marathi"}
STANDARDS = {9, 10}
GENERATION_STATUSES = {"complete", "partial"}

_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")

_TOP_KEYS = {"metadata", "content_chunks", "generation_status"}
_META_REQUIRED = {
    "file_name": str,
    "subject": str,
    "language": str,
    "board": str,
    "schema_version": str,
    "content_version": int,
    "last_updated": str,
}
_CHUNK_REQUIRED = {
    "chunk_id": str,
    "heading": str,
    "text": str,
    "difficulty": int,
    "importance": int,
}
_CHUNK_OPTIONAL = {
    "keywords", "linked_concepts", "prerequisites", "latex", "solution_steps",
    # v3.1 chunk enrichments (all optional; the app indexes what it knows).
    "topic", "explanation", "key_points", "formulas", "equations",
    "worked_example", "practice_problems", "examples", "key_terms",
    "key_figures", "data_points", "common_doubts", "provisions", "timeline",
}
_STEP_REQUIRED = {"text": str, "latex": str, "verified": bool}


def _check_str_list(value, label: str, errors: list[str]) -> None:
    if not isinstance(value, list) or not all(isinstance(v, str) for v in value):
        errors.append(f"{label} must be a list of strings")


def _validate_chunk(chunk: dict, idx: int, errors: list[str]) -> None:
    label = f"content_chunks[{idx}]"
    if not isinstance(chunk, dict):
        errors.append(f"{label} must be an object")
        return
    unknown = set(chunk) - set(_CHUNK_REQUIRED) - _CHUNK_OPTIONAL
    if unknown:
        errors.append(f"{label} has unknown keys: {sorted(unknown)}")
    for key, typ in _CHUNK_REQUIRED.items():
        if key == "text":
            # The body lives in "text" (v3.0) or "explanation" (v3.1); a pure
            # worked-example chunk carries it in the example's problem (the
            # app's Chunk.body falls back the same way).
            has_body = (
                isinstance(chunk.get("text"), str)
                or isinstance(chunk.get("explanation"), str)
                or isinstance((chunk.get("worked_example") or {}).get("problem"), str)
            )
            if not has_body:
                errors.append(f"{label} needs a body in 'text', 'explanation' or 'worked_example.problem'")
            continue
        if not isinstance(chunk.get(key), typ):
            errors.append(f"{label}.{key} missing or not {typ.__name__}")
    for key in ("difficulty", "importance"):
        value = chunk.get(key)
        if isinstance(value, int) and not 1 <= value <= 5:
            errors.append(f"{label}.{key} must be in 1..5, got {value}")
    for key in ("keywords", "linked_concepts", "prerequisites"):
        if key in chunk:
            _check_str_list(chunk[key], f"{label}.{key}", errors)
    if "latex" in chunk and not isinstance(chunk["latex"], str):
        errors.append(f"{label}.latex must be a string")
    if "solution_steps" in chunk:
        steps = chunk["solution_steps"]
        if not isinstance(steps, list):
            errors.append(f"{label}.solution_steps must be a list")
            return
        for j, step in enumerate(steps):
            if not isinstance(step, dict):
                errors.append(f"{label}.solution_steps[{j}] must be an object")
                continue
            for key, typ in _STEP_REQUIRED.items():
                if not isinstance(step.get(key), typ):
                    errors.append(
                        f"{label}.solution_steps[{j}].{key} missing or not {typ.__name__}"
                    )
            unknown = set(step) - set(_STEP_REQUIRED)
            if unknown:
                errors.append(f"{label}.solution_steps[{j}] has unknown keys: {sorted(unknown)}")


def validate_document(doc: dict, name: str = "<doc>") -> list[str]:
    """Return a list of human-readable schema violations (empty = valid)."""
    errors: list[str] = []
    if not isinstance(doc, dict):
        return [f"{name}: document must be an object"]

    # A v3.1 document nests its chunks under chapters; a v3.0 one is flat.
    nested = "chapters" in doc
    top_keys = {"metadata", "chapters"} if nested else _TOP_KEYS
    unknown = set(doc) - top_keys
    if unknown:
        errors.append(f"unexpected top-level keys: {sorted(unknown)}")
    missing = ({"metadata", "chapters"} if nested else _TOP_KEYS) - set(doc)
    if missing:
        errors.append(f"missing top-level keys: {sorted(missing)}")

    meta = doc.get("metadata")
    if isinstance(meta, dict):
        # v3.1 metadata versions with "version" and may add fields (branch,
        # part, authored_by, note); only the core identity is required.
        meta_required = (
            {"file_name": str, "subject": str, "language": str, "board": str}
            if nested else _META_REQUIRED
        )
        for key, typ in meta_required.items():
            if not isinstance(meta.get(key), typ):
                errors.append(f"metadata.{key} missing or not {typ.__name__}")
        if not nested and meta.get("schema_version") != SCHEMA_VERSION:
            errors.append(f"metadata.schema_version must be {SCHEMA_VERSION!r}")
        if meta.get("standard") not in STANDARDS:
            errors.append(f"metadata.standard must be one of {sorted(STANDARDS)}")
        if meta.get("language") not in LANGUAGES:
            errors.append(f"metadata.language must be one of {sorted(LANGUAGES)}")
        if isinstance(meta.get("content_version"), int) and meta["content_version"] < 1:
            errors.append("metadata.content_version must be >= 1")
        if isinstance(meta.get("last_updated"), str) and not _DATE_RE.match(meta["last_updated"]):
            errors.append("metadata.last_updated must be YYYY-MM-DD")
    elif "metadata" in doc:
        errors.append("metadata must be an object")

    if nested:
        chapters = doc.get("chapters")
        if isinstance(chapters, list):
            seen_nested: set[str] = set()
            for ci, chapter in enumerate(chapters):
                if not isinstance(chapter, dict):
                    errors.append(f"chapters[{ci}] must be an object")
                    continue
                inner = chapter.get("content_chunks")
                if not isinstance(inner, list):
                    errors.append(f"chapters[{ci}].content_chunks must be a list")
                    continue
                for i, chunk in enumerate(inner):
                    _validate_chunk(chunk, i, errors)
                    cid = chunk.get("chunk_id") if isinstance(chunk, dict) else None
                    if isinstance(cid, str):
                        if cid in seen_nested:
                            errors.append(f"duplicate chunk_id {cid!r}")
                        seen_nested.add(cid)
        else:
            errors.append("chapters must be a list")

    chunks = doc.get("content_chunks")
    if isinstance(chunks, list):
        seen: set[str] = set()
        for i, chunk in enumerate(chunks):
            _validate_chunk(chunk, i, errors)
            cid = chunk.get("chunk_id") if isinstance(chunk, dict) else None
            if isinstance(cid, str):
                if cid in seen:
                    errors.append(f"duplicate chunk_id {cid!r}")
                seen.add(cid)
    elif "content_chunks" in doc:
        errors.append("content_chunks must be a list")

    if "generation_status" in doc and doc["generation_status"] not in GENERATION_STATUSES:
        errors.append(f"generation_status must be one of {sorted(GENERATION_STATUSES)}")

    return [f"{name}: {e}" for e in errors]
