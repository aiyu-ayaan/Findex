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
- [ ] Storage hero card with wavy progress
- [ ] Category tiles in `MaterialShapes`
- [ ] Bookmarks + recent files rows
- [ ] Quick tools cards, active-operation banner

### M5 — Explorer
- [ ] Multi-tab browsing (add / close / switch)
- [ ] Breadcrumbs, list / grid, sort sheet
- [ ] Long-press + drag-to-select range, tap toggle in selection mode
- [ ] Floating selection toolbar
- [ ] Item action bottom sheet
- [ ] Dialogs: rename, new folder, details, compress (format + password), extract, delete
- [ ] Cut / copy / paste across tabs
- [ ] Predictive back: selection → parent folder → previous screen

### M6 — Categories
- [ ] Category screen (MediaStore) with thumbnails (Coil)

### M7 — Tools
- [ ] Storage Analyzer (donut chart + largest files)
- [ ] Storage Cleaner (junk scan + clean via FGS)
- [ ] WhatsApp Cleaner (per media type)

### M8 — Settings
- [ ] Theme mode, dynamic color, hidden files, default view

---

## Post-MVP

### Archives+
- [ ] 7z / RAR / TAR extraction and creation
- [ ] In-archive preview without full extraction

### Network Storage
- [ ] Windows Network Share (SMB) client
- [ ] WebDAV and FTP / SFTP
- [ ] Credential manager & bookmarks

### Quality of life
- [ ] Recycle bin + undo delete
- [ ] Global search with index
- [ ] Duplicate finder
- [ ] Tab session persistence
- [ ] Tablet two-pane explorer
