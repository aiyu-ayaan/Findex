# Findex Development Roadmap & Tasks

Track progress and implementation milestones for Findex file explorer.
MVP scope and decisions: [`docs/MVP_PLAN.md`](docs/MVP_PLAN.md). Visual rules: [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md).

---

## Phase 1: Project Initialization & Core Architecture ✅
- [x] Multi-module Gradle configuration (`app`, `core`, `ui`)
- [x] Kotlin 2.4 + AGP 9.4 with Java 17 compatibility
- [x] Hilt dependency injection setup across all modules
- [x] Material 3 Expressive theme skeleton
- [x] Centralized localization (`ui/src/main/res/values/strings.xml` only)
- [x] Storage models and repository skeleton
- [x] Foreground Service skeleton
- [x] First home screen
- [x] `run.sh` runner script

---

## MVP

### M0 — Plan & design
- [x] MVP plan (`docs/MVP_PLAN.md`)
- [x] Design system (`docs/DESIGN_SYSTEM.md`)
- [x] TODO restructure, CLAUDE.md conventions

### M1 — Design tokens
- [x] Full light/dark schemes incl. surface container ladder (seed teal `#0D9488`)
- [x] Expressive shapes + `MotionScheme.expressive()`
- [x] Full type scale with emphasized roles
- [x] Spacing tokens (`Dimens`) and category tones (`FileCategory.tone`)

### M2 — Core engine
- [x] File listing with sort + hidden-file filter
- [x] Rename, new folder, delete, copy, move with progress
- [x] ZIP compress (AES password) & extract via zip4j
- [x] `OperationManager` queue + foreground service notification progress + cancel
- [x] `FileClipboard` shared across tabs
- [x] MediaStore category queries
- [x] Room (SQLite) database: `bookmarks`, `recent_files`
- [x] Settings repository (DataStore — config only)

### M3 — App shell
- [x] Type-safe Navigation Compose host with shared-axis transitions
- [x] Predictive back enabled (`enableOnBackInvokedCallback`)
- [x] All-files-access onboarding screen
- [x] Drawer routes wired
- [x] Adaptive launcher icon (themed/monochrome), splash screen, FileProvider

### M4 — Home
- [x] Storage hero card with wavy progress
- [x] Category tiles in `MaterialShapes`
- [x] Bookmarks + recent files rows
- [x] Quick tools cards, active-operation banner

### M5 — Explorer
- [x] Multi-tab browsing (add / close / switch)
- [x] Breadcrumbs, list / grid, sort sheet
- [x] Long-press + drag-to-select range, tap toggle in selection mode
- [x] Floating selection toolbar
- [x] Item action bottom sheet
- [x] Dialogs: rename, new folder, details, compress (format + password), extract, delete
- [x] Cut / copy / paste across tabs
- [x] Predictive back: selection → parent folder (animated preview) → previous screen
- [x] Listing cache + delayed spinner + per-folder scroll restore (instant back navigation)

### M6 — Categories
- [x] Category screen (MediaStore) with thumbnails (Coil)

### M7 — Tools
- [x] Storage Analyzer (donut chart + largest files)
- [x] Storage Cleaner (junk scan + clean via FGS)
- [x] WhatsApp Cleaner (per media type)

### M8 — Settings
- [x] Theme mode, dynamic color, hidden files, default view

---

### MVP polish (feedback)
- [x] 16 KB page-size compliant native libs
- [x] Onboarding asks storage + notification permissions as separate steps
- [x] Status bar icons follow the in-app theme, not the system theme
- [x] Discord-style push drawer + modern menu icon
- [x] Unified scalloped category shapes
- [ ] Verify drag-to-select on a physical device
- [x] Large-screen (tablet) layout pass

---

## Post-MVP

### Archives+
- [x] 7z / RAR / TAR extraction and creation (Commons Compress + xz; ZIP/RAR read via native 7-Zip incl. RAR5, 16 KB-aligned rebuild)
- [x] In-archive preview without full extraction (browse entries, open single files via cache)

### Network Storage
- [x] Windows Network Share (SMB) client (smbj; shares open as Explorer tabs)
- [ ] WebDAV and FTP / SFTP
- [x] Credential manager (Keystore-encrypted passwords, add/remove shares)
- [x] Copy / move / delete between device and shares (and share to share), New-tab location picker

### Quality of life
- [x] Recycle bin + undo delete (`.FindexTrash` per volume, 30-day purge, Undo snackbar)
- [x] Global search with index (MediaStore `Files` index, Home search icon)
- [x] Duplicate finder (size → 64 KB head hash → full SHA-256; one copy always kept)
- [x] Tab session persistence (Room `open_tabs`, restored on cold start)
- [x] Tablet two-pane explorer
