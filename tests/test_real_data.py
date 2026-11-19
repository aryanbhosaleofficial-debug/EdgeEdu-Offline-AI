"""The shipped repo state must itself pass the pipeline's guarantees."""
import json
from pathlib import Path

from content_pipeline.manifest import MANIFEST_NAME, PUBLIC_KEY_NAME, verify_manifest
from content_pipeline.schema import validate_document
from content_pipeline.solutions import verify_check

ROOT = Path(__file__).resolve().parents[1]
DATA_FILES = sorted(
    p for p in (ROOT / "data").rglob("*.json") if p.name != MANIFEST_NAME
)


def _all_chunks(doc):
    """Chunks whether flat (v3.0 content_chunks) or nested (v3.1 chapters)."""
    if "chapters" in doc:
        return [c for ch in doc["chapters"] for c in ch.get("content_chunks", [])]
    return doc.get("content_chunks", [])


def test_all_data_files_are_valid():
    assert len(DATA_FILES) == 33
    errors = []
    for path in DATA_FILES:
        doc = json.loads(path.read_text(encoding="utf-8"))
        errors += validate_document(doc, name=path.name)
    assert errors == []


def test_every_shipped_solution_step_is_verified():
    # The current curriculum ships no pre-verified solution_steps (runtime
    # step-by-step solving comes from the on-device MathEngine); any that DO
    # ship must be machine-verified and carry renderable latex.
    for path in DATA_FILES:
        doc = json.loads(path.read_text(encoding="utf-8"))
        for chunk in _all_chunks(doc):
            for step in chunk.get("solution_steps") or []:
                assert step["verified"] is True, f"{path.name} {chunk['chunk_id']}"
            if chunk.get("solution_steps"):
                assert "latex" in chunk


def test_every_file_has_substantial_content():
    for path in DATA_FILES:
        doc = json.loads(path.read_text(encoding="utf-8"))
        chunks = _all_chunks(doc)
        assert len(chunks) >= 20, f"{path.name} has only {len(chunks)} chunks"
        for chunk in chunks:
            # Mirrors the app's Chunk.body fallback chain.
            body = (
                chunk.get("explanation")
                or chunk.get("text")
                or " ".join(chunk.get("key_points") or [])
                or (chunk.get("worked_example") or {}).get("problem")
                or ""
            )
            assert body.strip(), f"{path.name} {chunk['chunk_id']} has an empty body"


def test_all_seed_checks_still_pass_in_sympy():
    for seed_path in sorted((ROOT / "content" / "solutions").glob("*.json")):
        seed = json.loads(seed_path.read_text(encoding="utf-8"))
        for sol in seed["solutions"]:
            verify_check(sol["check"])


def test_shipped_manifest_verifies():
    body = verify_manifest(ROOT, ROOT / "keys" / PUBLIC_KEY_NAME, installed_version=1)
    # 33 curriculum JSONs plus their 33 textbook PDFs, all hash-listed.
    json_files = [f for f in body["files"] if f.endswith(".json")]
    pdf_files = [f for f in body["files"] if f.endswith(".pdf")]
    assert len(json_files) == 33
    assert len(pdf_files) == 33
    assert len(body["files"]) == 66
