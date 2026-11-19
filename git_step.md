# Publishing EdgeEdu Content to GitHub Releases

The app downloads each student's class+medium slice (JSON **and** PDF) from a
GitHub Release at login. This doc is the step-by-step to publish that content.

- **Repo:** `aryanbhosale285-arch/EdgeEdu-Offline-AI`
- **Release tag the app expects:** `content-v5` (matches `content_version: 5` in the
  signed manifest; it's the default in `android/app/build.gradle.kts`)
- **What to upload:** everything in the `release_assets/` folder — **67 files**
  (33 JSON + 33 PDF + `manifest.json`), ~370 MB total.

> A GitHub Release is a **flat bucket**: no folders, and it rewrites every space in
> an asset name to a `.` (dot). The `release_assets/` folder already holds the files
> with those exact final names (e.g. `9th_Maths.1_English.pdf`,
> `10th_History.and.Political.Science_English.json`) — so what you upload is what the
> app fetches, with no surprises. **Always upload from `release_assets/`** (a flat
> folder), not from `data/` (nested in subfolders). Do **not** rename the files.

---

## Option A — Web UI (no tools needed)

1. Go to **https://github.com/aryanbhosale285-arch/EdgeEdu-Offline-AI/releases/new**
2. **Choose a tag** → type `content-v5` → "Create new tag: content-v5 on publish".
3. **Target:** `main`.
4. **Release title:** `Content v5 — 9th & 10th curriculum (JSON + PDF)`
5. **Attach binaries:** open `release_assets/` and drag **all 67 files** into the
   *"Attach binaries by dropping them here"* box.
   - Path: `d:\Aryan\Project\EdgeEdu_Offline_AI\EdgeEdu-Offline-AI\release_assets\`
   - Wait until **all 67** finish uploading (~370 MB — can take several minutes).
6. Click **Publish release**.

## Option B — GitHub CLI (faster, scripted)

Install once: `winget install GitHub.cli` then `gh auth login`.

```bash
cd "d:/Aryan/Project/EdgeEdu_Offline_AI/EdgeEdu-Offline-AI"
gh release create content-v5 ./release_assets/* \
  --title "Content v5 — 9th & 10th curriculum (JSON + PDF)" \
  --notes "Curriculum JSON + 9th & 10th textbook PDFs. Downloaded per class+medium on login. Manifest content_version 5."
```

To upload into / overwrite an existing release: `gh release upload content-v5 ./release_assets/* --clobber`

---

## Verify after publishing

Each file gets a permanent URL of this shape (note the dots where the file name had spaces):

```
https://github.com/aryanbhosale285-arch/EdgeEdu-Offline-AI/releases/download/content-v5/<asset-name>
```

Sanity-check a few in a browser (they should download):

- `…/releases/download/content-v5/manifest.json`
- `…/releases/download/content-v5/10th_Geography_English.json`
- `…/releases/download/content-v5/10th_Maths.1_English.pdf`
- `…/releases/download/content-v5/10th_History.and.Political.Science_English.json`

---

## Build the app against the release

The tag is already the default, so a normal build works once the release is live:

```bash
cd android
./gradlew assembleRelease
```

Override tag/owner/repo for a test release:

```bash
./gradlew assembleRelease \
  -PcontentReleaseTag=content-v5 \
  -PcontentGithubOwner=aryanbhosale285-arch \
  -PcontentGithubRepo=EdgeEdu-Offline-AI
```

Offline fallback (bundled JSON only, no download — dev without the release):
`-PcontentReleaseTag=` (empty). PDFs are never bundled, so full content needs the release.

> **Important:** Publish the release **before** distributing the APK. The app downloads
> from `content-v5` on login; if the release isn't up yet, login shows the
> "download failed — retry" screen.

---

## How the app names assets (why dots)

The app fetches `https://…/download/content-v5/<assetName>` where `assetName` is the
file's base name with **each space replaced by a `.`** — exactly GitHub's own
sanitization. This was verified against the live release (64 of 67 matched on the
first upload; the 3 misses were a stale typo, since fixed). Because the app mirrors
GitHub's transform, uploading the curriculum files just works — no manual renaming.

---

## Updating content later (new version)

1. Edit content under `data/`.
2. Rebuild + sign the manifest (bumps `content_version`):
   ```bash
   python -m tools.content_pipeline manifest-build
   python -m tools.content_pipeline manifest-verify
   ```
3. Re-stage the flat upload folder (GitHub-style dot names):
   ```bash
   python - <<'PY'
   import json, shutil
   from pathlib import Path
   root = Path(".")
   man = json.loads((root/"data/manifest.json").read_text(encoding="utf-8"))
   out = root/"release_assets"; shutil.rmtree(out, ignore_errors=True); out.mkdir()
   for rel in list(man["files"]) + ["data/manifest.json"]:
       shutil.copy(root/rel, out/Path(rel).name.replace(' ', '.'))
   print("staged", len(list(out.iterdir())), "assets")
   PY
   ```
4. Sync the bundled JSON fallback into the APK assets:
   ```bash
   rm -rf android/app/src/main/assets/data && mkdir -p android/app/src/main/assets/data
   cp -r data/. android/app/src/main/assets/data/
   find android/app/src/main/assets/data -name '*.pdf' -delete   # PDFs stay host-only
   ```
5. Create a **new** release tag (e.g. `content-v6`) and upload `release_assets/*`.
   The version is monotonic — devices reject any older `content_version` (downgrade
   protection), so always use a fresh tag matching the new version.
6. Update the default tag in `android/app/build.gradle.kts` (`contentReleaseTag`).

---

## Google Drive backup (cold storage)

Drive is a **manual backup**, not a live mirror — its share links aren't predictable
per-file URLs, so the app does not read from it. Keep a copy of `release_assets/`
(or a single zip of it) in Drive for safety.

- **Drive backup link:** _<paste link here>_
- **Backup contents:** the 67 files from `release_assets/` for tag `content-v5`.

> To restore from Drive: download the files and re-upload them to a GitHub Release
> with the same tag and the same (unchanged) names.
