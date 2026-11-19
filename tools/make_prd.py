"""Regenerate EdgeEdu_PRD.pdf (the product requirements document).

    python tools/make_prd.py

Keep this script the single source of truth for the PRD: edit the CONTENT
below and re-run, rather than editing the PDF ad hoc.
"""
from __future__ import annotations

from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    ListFlowable, ListItem, PageBreak, Paragraph, SimpleDocTemplate, Spacer,
    Table, TableStyle,
)

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "EdgeEdu_PRD.pdf"

DOC_VERSION = "PRD v3.7"
APP_VERSION = "0.5.2 (versionCode 7)"
DOC_DATE = "6 July 2026"

# Windows ships Nirmala UI, which covers Latin + Devanagari; fall back to
# Helvetica (Devanagari examples then render as boxes but the build succeeds).
BODY_FONT, BOLD_FONT = "Helvetica", "Helvetica-Bold"
for candidate in (Path("C:/Windows/Fonts/Nirmala.ttf"), Path("C:/Windows/Fonts/Nirmala.ttc")):
    if candidate.is_file():
        # Nirmala ships as a collection on recent Windows: index 0 = regular,
        # index 1 = bold.
        pdfmetrics.registerFont(TTFont("Body", str(candidate), subfontIndex=0))
        try:
            pdfmetrics.registerFont(TTFont("BodyBold", str(candidate), subfontIndex=1))
        except Exception:
            pdfmetrics.registerFont(TTFont("BodyBold", str(candidate), subfontIndex=0))
        BODY_FONT, BOLD_FONT = "Body", "BodyBold"
        break

styles = getSampleStyleSheet()
BODY = ParagraphStyle("BodyX", parent=styles["BodyText"], fontName=BODY_FONT,
                      fontSize=9.5, leading=13, spaceAfter=4)
H1 = ParagraphStyle("H1X", parent=styles["Heading1"], fontName=BOLD_FONT,
                    fontSize=13, leading=16, spaceBefore=10, spaceAfter=4,
                    textColor=colors.HexColor("#1a3c6e"))
H2 = ParagraphStyle("H2X", parent=styles["Heading2"], fontName=BOLD_FONT,
                    fontSize=10.5, leading=13, spaceBefore=6, spaceAfter=2,
                    textColor=colors.HexColor("#1a3c6e"))
TITLE = ParagraphStyle("TitleX", parent=styles["Title"], fontName=BOLD_FONT,
                       fontSize=24, leading=28, spaceAfter=2)
SUB = ParagraphStyle("SubX", parent=BODY, fontSize=12, leading=15,
                     textColor=colors.HexColor("#444444"))


def bullets(items: list[str]):
    return ListFlowable(
        [ListItem(Paragraph(i, BODY), leftIndent=14) for i in items],
        bulletType="bullet", start="•", leftIndent=10, bulletFontName=BODY_FONT,
    )


def kv_table(rows: list[tuple[str, str]], col0: float = 4.2):
    t = Table(
        [[Paragraph(f"<b>{k}</b>", BODY), Paragraph(v, BODY)] for k, v in rows],
        colWidths=[col0 * cm, (17.0 - col0) * cm],
    )
    t.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("LINEBELOW", (0, 0), (-1, -2), 0.25, colors.HexColor("#dddddd")),
    ]))
    return t


def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont(BODY_FONT, 8)
    canvas.setFillColor(colors.HexColor("#888888"))
    canvas.drawString(2 * cm, 1.2 * cm, "EdgeEdu — Offline AI Tutor  ·  Product Requirements Document")
    canvas.drawRightString(A4[0] - 2 * cm, 1.2 * cm, str(canvas.getPageNumber()))
    canvas.restoreState()


def build():
    story = []

    # ---- Cover -------------------------------------------------------------
    story += [
        Spacer(1, 3 * cm),
        Paragraph("EdgeEdu", TITLE),
        Paragraph("Offline AI Tutor for Maharashtra Board, Class 9–10", SUB),
        Paragraph("Product Requirements Document", SUB),
        Spacer(1, 0.8 * cm),
        Paragraph(
            "A fully offline learning app that downloads a student's class and medium after a local "
            "login, then teaches every subject — and solves custom maths and physics problems step by "
            "step — on the device, with no internet required after setup.", BODY),
        Spacer(1, 0.8 * cm),
        kv_table([
            ("Document version", DOC_VERSION),
            ("App version", APP_VERSION),
            ("Date", DOC_DATE),
            ("Platform", "Android 8.0+ (minSdk 26), arm64 / 2 GB RAM target"),
            ("Boards / Classes", "Maharashtra State Board, Std 9 and Std 10"),
            ("Media", "English, Hindi, Marathi"),
            ("Content", "content-v7 (signed manifest, 33 curriculum files + 33 textbook PDFs)"),
            ("Status", "Phases 1–3 implemented; on-device LLM opt-in"),
        ]),
        PageBreak(),
    ]

    # ---- 1. Overview ---------------------------------------------------------
    story += [
        Paragraph("1. Overview &amp; Vision", H1),
        Paragraph(
            "EdgeEdu is an offline-first study companion for Maharashtra Board students in Classes 9 "
            "and 10. Many learners study on low-end phones with little or intermittent connectivity. "
            "EdgeEdu solves this by downloading exactly the content a student needs — their class and "
            "medium — once, and then running everything (reading, search, and an AI tutor) on the "
            "device itself.", BODY),
        Paragraph(
            "The tutor does not just give answers. It teaches the method as short, numbered steps a "
            "student can learn from, and for any maths or physics problem the student types, it works "
            "the solution out with a verified computation engine — never guessing a number. The "
            "curriculum itself ships 300+ machine-verified worked examples that surface as "
            "step-by-step solutions in chat, browse and search, and ~490 textbook questions "
            "(common doubts and practice problems) that the tutor answers verbatim from the "
            "textbook — every one of them exercised by an automated test.", BODY),
        Paragraph("Goals", H2),
        bullets([
            "Work fully offline after first-time setup (content + optional AI model).",
            "Download only the student's class + medium slice — small and fast, "
            "'like a game downloading before play'.",
            "Teach step by step and show the reasoning behind each step.",
            "Solve custom maths problems (arithmetic, linear and quadratic) with verified, checkable working.",
            "Answer doubts in every subject the way students actually phrase them "
            "('explain…', 'what is…', '…क्या है'), grounded in the textbook.",
            "Let students bring their own material: any uploaded file becomes first-class, "
            "tutor-answerable content in the same format as the shipped subjects.",
            "Localise the whole UI to the chosen medium (English / Hindi / Marathi).",
            "Run acceptably on a 2 GB RAM device.",
        ]),
        Paragraph("Non-goals (for now)", H2),
        bullets([
            "Boards/classes beyond Maharashtra Std 9–10.",
            "Server-side grading or social features (the optional account is backup-only).",
            "Solving symbolic calculus / proofs (scope is school arithmetic, linear, quadratic and ratio values).",
            "Running the neural model on devices without enough free RAM (graceful fallback instead).",
        ]),

        # ---- 2. Users ----------------------------------------------------------
        Paragraph("2. Target Users &amp; Device", H1),
        kv_table([
            ("Primary user", "Std 9–10 student, Maharashtra Board, studying offline."),
            ("Secondary", "Hindi/Marathi-medium students who need a fully translated UI."),
            ("Device target", "Android 8.0+ (API 26+), arm64-v8a, as low as 2 GB RAM."),
            ("Connectivity", "Online once for setup; fully offline thereafter."),
            ("Storage", "Content slice is small; optional AI model adds ~2 GB."),
        ]),

        # ---- 3. Scope ------------------------------------------------------------
        Paragraph("3. Scope: Subjects &amp; Content", H1),
        Paragraph(
            "Content covers five subject areas per class, in three media. Maths and Science are split "
            "into their two textbook parts so the tutor knows exactly which book a question belongs "
            "to. Std 9 Science is a single combined book; Std 10 splits into Part 1 and Part 2.", BODY),
        Paragraph("Subjects shown per class", H2),
        kv_table([
            ("Std 9", "Maths 1, Maths 2, Science, Geography, Social Studies / History"),
            ("Std 10", "Maths 1, Maths 2, Science 1, Science 2, Geography, Social Studies / History"),
        ]),
        Paragraph(
            "Each chunk's subject is derived from its file name (e.g. '10th_Maths 1_English.json' → "
            "'Maths 1'), which is the one consistent signal; the in-file subject metadata is localised "
            "and irregular and is not relied on. The home screen only shows subjects that actually "
            "have content for the logged-in class. Students can also add their own subjects, whose "
            "content comes entirely from uploaded files (§8).", BODY),
        Paragraph("Content schema", H2),
        Paragraph(
            "Curriculum files use a nested JSON schema: a top-level {metadata, chapters}, where each "
            "chapter holds content_chunks. A chunk carries a heading, an explanation (the body text), "
            "key_points, keywords, difficulty/importance, and — for maths and science — an optional "
            "worked_example: a problem statement, machine-verified solution steps (prose + LaTeX, each "
            "marked verified), and the final answer. Chunks also carry common_doubts and "
            "practice_problems (question/answer pairs), key_terms, formulas and equations. The app "
            "reads both the nested layout and a flat content_chunks list (used by imported notes), "
            "and tolerates missing bookkeeping fields and null lists so irregular files still load.", BODY),
        Paragraph("Content quality (enforced by tests)", H2),
        bullets([
            "chunk_ids are unique per file (two-part books — History + Civics/Political Science — are "
            "renumbered so their chapters do not collide).",
            "Every chunk has a non-empty body (explanation, text, key points or a worked example's problem).",
            "Every shipped worked-example step is machine-verified (312 worked examples, 800+ verified steps).",
            "Metadata is normalised: canonical language names and ISO dates.",
        ]),

        # ---- 4. Delivery -----------------------------------------------------------
        Paragraph("4. Content Delivery (login-gated download)", H1),
        Paragraph(
            "After the student creates a local profile (name, class, medium) and logs in, the app "
            "downloads just that class+medium slice of JSON and PDF files — the way a game downloads "
            "its assets before the first match. On logout the downloaded content is removed.", BODY),
        bullets([
            "Scope filter: a file is in scope when its path contains the class folder (/9th/ or /10th/) "
            "and ends with _&lt;Medium&gt;.json or _&lt;Medium&gt;.pdf.",
            "Primary host: GitHub Releases (a flat asset bucket, current tag content-v7). "
            "A Google Drive copy is kept as backup.",
            "GitHub sanitises spaces in asset names to dots; the app maps manifest paths to asset names "
            "the same way so downloads match.",
            "Downloads stream with resume support (HTTP Range / 206) and survive interruptions.",
            "Source order: GitHub release → HTTP host → bundled assets (offline dev fallback).",
        ]),
        Paragraph("Integrity", H2),
        Paragraph(
            "Every file is listed in a signed manifest (manifest.json) with its SHA-256 and size. The "
            "manifest carries an Ed25519 signature. On load, each scoped JSON is hash-verified against "
            "the manifest before use; a missing file or hash mismatch raises an integrity error rather "
            "than showing tampered content. Content versions are monotonic: a downgrade is rejected at "
            "every login.", BODY),
        Paragraph("Resilience: never hard-fail when content already ships in the APK", H2),
        Paragraph(
            "A remote origin (GitHub release or HTTP host) is always wrapped in a fallback source: if "
            "the remote fails for any reason (unpublished release, 404, offline, cleartext blocked), "
            "provisioning retries against the identical bundled-asset content instead of leaving the "
            "student stuck on a failed download. The fallback latches its manifest to whichever origin "
            "answered first, so a bundled manifest is never paired with partially-fetched remote files. "
            "Within a provision, JSON files (all searchable content) are mandatory and any failure to "
            "fetch or verify one still aborts the whole download; textbook PDFs are a reader convenience "
            "and are skipped individually when the origin cannot supply them (the bundled-asset origin "
            "ships JSON only, to keep the APK small) — every file that IS fetched is still SHA-256 "
            "verified.", BODY),

        # ---- 5. Tutor -----------------------------------------------------------
        Paragraph("5. The AI Tutor", H1),
        Paragraph(
            "The tutor follows a retrieve → explain → verify pipeline. Retrieval grounds the "
            "explanation in the student's own textbook; the explanation model teaches the method; and "
            "any calculation is routed to a verified maths engine instead of being guessed.", BODY),
        Paragraph("Pipeline", H2),
        bullets([
            "Retrieve: a BM25 index (Unicode-aware, handles Latin and Devanagari) finds the most "
            "relevant chunks for the question. Retrieval is stopword-aware: conversational filler "
            "('what is…', 'explain…', 'क्या है', 'म्हणजे काय') carries no weight, so a doubt phrased as a "
            "sentence ranks — and gates — on its topic words. Latin accents fold, so typing "
            "'Rene Descartes' matches the textbook's 'René Descartes'. The tokenizer keeps Devanagari "
            "vowel signs (combining marks) inside tokens, so Hindi/Marathi words match whole.",
            "Custom-problem solver: if the student typed a solvable maths problem, it is solved step by "
            "step with verified working — even when the textbook does not cover it.",
            "Textbook Q&amp;A: the curriculum ships the questions students actually ask — common doubts "
            "and practice problems, with answers and hints. A typed question matching one (Jaccard "
            "similarity of content words ≥ 0.6; an exact textbook question scores 1.0) is answered "
            "verbatim from the textbook, with the source cited. This applies equally to questions in "
            "uploaded files (§8).",
            "Worked examples: when the best-matching chunk carries a verified worked example, its steps "
            "ride along with the answer and render with LaTeX.",
            "Explain: the model teaches from the retrieved context as short numbered steps, and emits "
            "&lt;calc&gt;…&lt;/calc&gt; tool calls for any arithmetic.",
            "Intercept &amp; verify: a calc interceptor replaces each &lt;calc&gt; call with an "
            "engine-computed, substitution-checked result before it reaches the student.",
            "Decline honestly: outside a compute session, if retrieval coverage is too low the tutor "
            "says it cannot find the topic instead of inventing an answer.",
        ]),
        Paragraph("Chapter-focused sessions", H2),
        Paragraph(
            "Tapping a subject on the home screen opens a chapter picker before the chat session "
            "starts, so retrieval can be scoped tighter than 'the whole subject'. Choosing a chapter "
            "builds the session's BM25 index from only that chapter's chunks (plus the student's own "
            "imported notes for the subject, which stay available either way), so a doubt asked inside "
            "a chapter is answered precisely instead of competing against every other chapter's "
            "content; 'Whole subject' keeps the original ask-across-everything behaviour. The session "
            "header shows the focused chapter (e.g. 'Geography · Field Visit') so the student always "
            "sees what the tutor is scoped to. A subject with only one chapter (or a flat, unchaptered "
            "file) skips the picker and opens directly.", BODY),
        Paragraph(
            "The chat input's placeholder example also follows the active subject instead of always "
            "showing a maths equation — e.g. Geography prompts 'what is a field visit?', History "
            "prompts 'what is historiography?' — localised in all three media.", BODY),
        Paragraph("On-device language model", H2),
        kv_table([
            ("Model", "Qwen2.5-3B Instruct (Q4_K_M GGUF), ~2 GB."),
            ("Runtime", "llama.cpp via a JNI bridge (arm64-v8a), built with -PenableLlama."),
            ("Download", "Opt-in only, from Settings ('Download AI model'); streamed from Hugging Face with resume."),
            ("Fallback", "Until the model is present, an extractive engine assembles answers from "
                         "retrieved chunks — excerpt plus the chunk's key points — so the whole "
                         "pipeline still works."),
            ("Low-RAM note", "On a 2 GB device the model may not fit in memory; the verified maths "
                             "solver and content features run without it."),
        ]),

        # ---- 6. Maths engine ---------------------------------------------------
        Paragraph("6. Verified Maths Engine", H1),
        Paragraph(
            "Computation is done by mXparser, never by the language model. The engine solves four "
            "problem types and shows every step, each one verifiable:", BODY),
        bullets([
            "Arithmetic: evaluates an expression by BODMAS and shows the worked result (e.g. 15*3 + 7).",
            "Linear equations: solves one-variable equations algebraically and verifies the root by "
            "substituting it back.",
            "Quadratic equations: standard form → identify a, b, c → discriminant D = b² − 4ac → "
            "quadratic formula → two roots (or a repeated root, or 'no real roots' when D &lt; 0), "
            "each verified by substitution.",
            "Ratio-value questions ('if a/b = 7/3, find (a² + b²)/(a² − b²)'): teaches the k-method "
            "(a = 7k, b = 3k), evaluates the expression, returns an exact fraction (29/20), and "
            "verifies scale-independence by re-evaluating at k = 2 — an expression where k does not "
            "cancel is refused rather than answered wrongly.",
        ]),
        Paragraph(
            "The engine understands the symbol-bar characters students insert — superscripts (x^2 from "
            "the 'x²' key), roots, pi, and the multiply/divide and minus signs — strips command words "
            "in any script, and tolerates sentence punctuation, so 'solve for x: 2x + 3 = 7', "
            "'what is 15*3 + 7?' or the Hindi/Marathi equivalent all reduce to the bare maths. "
            "A problem it cannot solve cleanly is handed to the explanation model "
            "(e.g. a physics word problem).", BODY),
        Paragraph("Maths symbol bar", H2),
        Paragraph(
            "The chat input has a scrollable symbol bar so students can type maths without a special "
            "keyboard. Tapping a symbol inserts it at the cursor; superscripts are real Unicode "
            "characters so x² renders small inline in the input, the sent message, and retrieval. "
            "Symbols: square root, cube root, x², x³, xⁿ (power), pi, theta, multiply, divide, half, "
            "&lt;=, &gt;=, not-equal, degree, and bracket pair.", BODY),

        # ---- 7. Localisation ------------------------------------------------------
        Paragraph("7. Localisation", H1),
        Paragraph(
            "Localisation is driven by the chosen medium, not the device locale. When a student picks "
            "Hindi or Marathi medium, the entire UI switches to that language via a Compose "
            "CompositionLocal carrying the active string set. Subject names localise too (e.g. "
            "Maths 1 → गणित 1, Science 1 → विज्ञान 1). Search, browse, chat, settings and home are all "
            "translated, and retrieval treats Hindi/Marathi question fillers as stopwords so doubts "
            "asked naturally in Devanagari ground correctly.", BODY),

        # ---- 8. Reading -----------------------------------------------------------
        Paragraph("8. Reading, Search &amp; Bring-Your-Own Content", H1),
        bullets([
            "Home (per the product sketch): a 'Hello &lt;name&gt; / Class N' greeting header, then every "
            "subject as a full-width row with a study-progress bar. Progress is real, not decorative: "
            "distinct topics the student engaged with (chunks cited in tutor answers + bookmarks) ÷ "
            "the subject's total topics, stored on-device and kept across logout.",
            "Browse: read the active subject's books chunk by chunk, with verified-solution badges and "
            "inline LaTeX (badges count worked examples too).",
            "Search: BM25 search across the downloaded content with language and subject filters.",
            "Saved: bookmark chunks for quick revision.",
            "LaTeX rendering: formulas render via a bundled KaTeX distribution in an offline WebView; "
            "the render auto-sizes so long step-by-step answers are never clipped.",
        ]),
        Paragraph("Upload pipeline (bring your own content)", H2),
        Paragraph(
            "A student can import a .txt / .md / PDF / JSON / photo into any subject, or create a whole "
            "new subject from their own files. An on-device structuring pipeline (heuristic, no "
            "network) turns the upload into the SAME v3.1 curriculum shape the shipped subjects use, "
            "so uploaded material is first-class — searchable and tutor-answerable exactly like the "
            "built-in textbooks. It:", BODY),
        bullets([
            "Splits the text into chapters at heading lines (markdown '#', 'Chapter N', numbered or "
            "ALL-CAPS titles) and each chapter into retrieval-sized chunks with a heading and keywords.",
            "Extracts the enrichments the tutor understands: 'Q: … / A: …' (or a question line followed "
            "by its answer) becomes a common_doubts entry answered verbatim; 'Term: meaning' lines "
            "become key_terms; equation-looking lines become formulas; bullet lines become key_points.",
            "Round-trips losslessly when the upload already is a curriculum JSON — its chapters, "
            "worked examples and Q&amp;A are kept as-is rather than flattened to prose.",
            "Writes the structured result as an offline .json file in the exact shipped format, and "
            "labels every answer's source as the student's notes (📝) versus the textbook (📄).",
        ]),
        Paragraph(
            "PDFs are text-extracted (PdfBox) and photos are OCR'd on-device; validation (type, 50 MB "
            "cap) runs before any bytes are parsed, since uploads are untrusted input. Imported "
            "content is user data: kept across logout and never uploaded anywhere.", BODY),

        # ---- 9. Architecture ------------------------------------------------------
        Paragraph("9. Architecture &amp; Tech Stack", H1),
        kv_table([
            ("UI", "Jetpack Compose (Material 3), single-activity, StateFlow-driven AppViewModel."),
            ("Language", "Kotlin; kotlinx.serialization for content; coroutines for IO."),
            ("Search", "In-process BM25 index, Unicode/Devanagari tokenizer (combining-mark aware) "
                       "with stopword filtering and Latin accent folding; indexes body, heading, "
                       "keywords, textbook Q&amp;A, key terms, formulas and examples."),
            ("Maths", "mXparser (verified compute) + a step-by-step solver layer."),
            ("Uploads", "On-device structuring pipeline (NoteStructurer) → v3.1 curriculum JSON; "
                        "stored in SQLite with the full chunk as JSON and re-exported as a file."),
            ("LLM", "llama.cpp JNI bridge (optional native build) with a mock extractive fallback."),
            ("Rendering", "KaTeX in an offline WebView via WebViewAssetLoader."),
            ("Content", "Python pipeline producing an Ed25519-signed SHA-256 manifest; schema "
                        "validation covers both the flat v3.0 and nested v3.1 document shapes."),
            ("Sessions", "One subject active at a time; its chunks, index and tutor tooling are built "
                         "per session and freed on switch."),
        ]),

        # ---- 10. Security -----------------------------------------------------------
        Paragraph("10. Security &amp; Privacy", H1),
        bullets([
            "Local-only login: profiles live on the device; there is no account server.",
            "Signed, hash-verified content: SHA-256 per file against an Ed25519-signed manifest.",
            "Offline by design: the AI runs locally; no student data leaves the device unless the student opts into the backup account (§12) — and then only their own Q&A history.",
            "Untrusted uploads: type and size are validated before parsing; imported files stay on-device.",
            "Sandboxed rendering: the KaTeX WebView loads only in-APK assets, with no remote URLs and "
            "JavaScript limited to KaTeX.",
            "Scoped storage: only the logged-in class+medium is on disk; logout removes downloaded content.",
        ]),

        # ---- 11. Build --------------------------------------------------------------
        Paragraph("11. Build &amp; Release", H1),
        kv_table([
            ("Build", "Gradle (local toolchain: gradle 8.10.2, JDK 17), assembleDebug → app-debug.apk."),
            ("Tests", "83 Android unit tests (incl. end-to-end tutor runs over the real shipped "
                      "curriculum, the upload pipeline, and the content-fallback/PDF-optional/chapter "
                      "lifecycle) + 31 Python content-pipeline tests."),
            ("Distribution", "Renamed EdgeEdu.apk, published on the GitHub release."),
            ("Content release", "Flat asset bucket at tag content-v7 (regenerate release_assets/ from "
                                "data/ and upload when content changes)."),
            ("Versioning", "versionName shown on the home screen ('Welcome back — v0.4.0') so the "
                           "installed build is verifiable."),
            ("Native LLM", "Optional: -PenableLlama with llama.cpp + Android NDK for arm64-v8a."),
            ("Install", "Sideload the APK; uninstall the old build and re-download fresh to update."),
        ]),

        # ---- 12. Account & history sync -------------------------------------------
        Paragraph("12. Accounts, Profiles &amp; History Sync", H1),
        Paragraph("Two-tier login (Netflix-style)", H2),
        Paragraph(
            "Signing in has two tiers, like a streaming app. Tier 1 is the optional CLOUD account "
            "(email + password): it backs up Q&amp;A history when online, is fully skippable, and the "
            "app never requires it. Tier 2 is the LOCAL profiles — a 'Who's studying?' picker: one "
            "device holds several profiles (name, class, medium), so siblings can share a phone with "
            "separate classes and mediums. Picking a profile loads its content; if the device holds a "
            "different class+medium slice, the right one is downloaded automatically. Logout "
            "deselects the profile and frees downloaded content but KEEPS the saved profiles — "
            "logging back in is one tap. Existing single-profile installs migrate automatically.", BODY),
        Paragraph("History backup", H2),
        Paragraph(
            "Every question the student asks — and the tutor's full answer, steps included — is "
            "recorded in a local history database on the device, entirely offline. Optionally, the "
            "student can create a backup account (email + password, from Settings); the moment the "
            "device comes online, pending history uploads automatically to the account's database, "
            "so nothing is lost between connectivity windows.", BODY),
        bullets([
            "Local-first: history is written on-device for every exchange with a `synced` flag; the "
            "app never blocks on the network.",
            "Auto-sync: a connectivity callback fires when the device gets internet and uploads "
            "pending rows oldest-first in batches; rows are marked synced only after the server "
            "accepts them, so an interrupted sync resumes without loss or duplication (the server "
            "dedupes on account + client id).",
            "Account: create / sign in from Settings (online only; passwords are never stored on "
            "device — only the auth token). Sign-out keeps local history.",
            "Backend: any server implementing three JSON endpoints (auth/signup, auth/login, "
            "history). The repo ships tools/sync_server.py — a MongoDB-backed reference server "
            "configured by the repo .env (MONGODB_URI); the app's endpoint is baked in at build "
            "time from SYNC_BASE_URL (.env / env var / -PsyncBaseUrl). Unconfigured builds still "
            "record history locally and say so in Settings.",
        ]),

        # ---- 13. History --------------------------------------------------------------
        Paragraph("13. Version History", H1),
        kv_table([
            ("0.3.0", "Login-gated per-class download; content over the internet (GitHub Releases + "
                      "Drive backup); step-by-step tutor; opt-in AI model download; medium-driven "
                      "localisation."),
            ("0.3.1", "Version surfaced on the home screen; maths symbol bar added to the chat input."),
            ("0.3.2", "Quadratic-equation solver with verified steps; symbol-bar characters understood "
                      "by the parser; auto-sizing maths render; Maths/Science split into Part 1 / Part 2."),
            ("0.4.0", "Doubt solving fixed for every subject: stopword-aware retrieval (English + "
                      "Hindi + Marathi), accent folding and combining-mark-aware tokenization stop "
                      "conversational questions being declined or mis-grounded. 312 verified worked "
                      "examples render as step-by-step solutions. Textbook question answering: ~490 "
                      "shipped common doubts and practice problems are indexed and answered verbatim "
                      "(all covered by an automated test across both standards and all three media). "
                      "Upload pipeline: any imported file (or a whole new subject) is structured into "
                      "the shipped v3.1 curriculum format — chapters, chunks, and extracted Q&amp;A / "
                      "key terms / formulas — so uploaded questions are answered like textbook ones; "
                      "curriculum JSON round-trips losslessly. Maths parser tolerates sentence "
                      "punctuation and solves ratio-value questions with the k-method, exact fractions and a "
                      "scale-independence check. Content repaired (unique chunk ids, duplicate chapters "
                      "removed, metadata normalised) and re-signed as content-v7."),
            ("0.5.0", "Account & history sync: every Q&A is saved to an on-device history store "
                      "(offline), and an optional email/password backup account uploads it "
                      "automatically when the device comes online — batched, resumable and "
                      "idempotent. MongoDB-backed reference sync server (tools/sync_server.py) "
                      "configured via .env; app endpoint via SYNC_BASE_URL. Settings gains the "
                      "Account & backup card in all three media."),
            ("0.5.1", "Home screen redesigned to the product sketch: greeting header ('Hello <name>, "
                      "Class N') and full-width subject rows with per-subject study-progress bars "
                      "(progress = engaged topics ÷ total, tracked on-device). Two-tier Netflix-style "
                      "login: optional cloud account on top, multi-profile 'Who's studying?' picker "
                      "below — multiple local profiles per device, one-tap switching with automatic "
                      "class+medium re-download, logout keeps saved profiles."),
            ("0.5.2", "Reliability: a remote content origin now falls back to the identical bundled-asset "
                      "content on any failure (unpublished release, 404, offline), and textbook PDFs are "
                      "optional during provisioning (JSON stays mandatory) — the app no longer hard-fails "
                      "when a PDF isn't available, fixing a production content-load error. Added a "
                      "network security config permitting cleartext HTTP to the local sync server. "
                      "Chapter-focused sessions: tapping a subject opens a chapter picker so the tutor "
                      "can be scoped to one chapter for more precise doubt-solving, with 'Whole subject' "
                      "as a fallback and the focused chapter shown in the session header. The Ask AI "
                      "placeholder example now follows the active subject instead of always showing a "
                      "maths equation. New tools/deploy.sh adb helper for a fast build-install-test loop."),
        ], col0=2.6),

        # ---- 14. Roadmap ----------------------------------------------------------------
        Paragraph("14. Roadmap", H1),
        bullets([
            "Ship the native llama.cpp build so the neural tutor runs on capable devices.",
            "Smaller / quantised model option for 2–3 GB phones.",
            "Expand verified solving (simultaneous equations, basic mensuration).",
            "More subjects and later standards once the pipeline is proven.",
            "Full Hindi/Marathi typed-maths handling end to end.",
        ]),
    ]

    doc = SimpleDocTemplate(
        str(OUT), pagesize=A4,
        leftMargin=2 * cm, rightMargin=2 * cm, topMargin=1.8 * cm, bottomMargin=1.8 * cm,
        title="EdgeEdu — Product Requirements Document",
        author="EdgeEdu",
    )
    doc.build(story, onFirstPage=footer, onLaterPages=footer)
    print(f"wrote {OUT} ({OUT.stat().st_size:,} bytes)")


if __name__ == "__main__":
    build()
