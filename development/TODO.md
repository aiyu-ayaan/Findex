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
- [x] Brand mark ("Index F") for launcher, themed icon and README; animated splash with M3 motion and a timed exit

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
- [x] Verify drag-to-select on a physical device
- [x] Large-screen (tablet) layout pass

---

## Post-MVP

### Archives+
- [x] 7z / RAR / TAR extraction and creation (Commons Compress + xz; ZIP/RAR read via native 7-Zip incl. RAR5, 16 KB-aligned rebuild)
- [x] In-archive preview without full extraction (browse entries, open single files via cache)

### Network Storage
- [x] Windows Network Share (SMB) client (smbj; shares open as Explorer tabs)
- [x] WebDAV and FTP / SFTP (`WebDavSession`, `FtpSession` via Commons Net, `SftpSession` via JSch; Room schema v5 `protocol` + `port`)
- [x] Credential manager (Keystore-encrypted passwords, add/remove shares)
- [x] Copy / move / delete between device and shares (and share to share), New-tab location picker
- [x] SMB / SFTP / FTP / WebDAV image/video thumbnails (`ThumbFetcher` + `SmbRandomAccessFile`) and direct video streaming in default app (`SmbContentProvider` + `openProxyFileDescriptor`)

### Quality of life
- [x] Recycle bin + undo delete (`.FindexTrash` per volume, 30-day purge, Undo snackbar)
- [x] Global search with index (MediaStore `Files` index, Home search icon)
- [x] Duplicate finder (size → 64 KB head hash → full SHA-256; one copy always kept)
- [x] Tab session persistence (Room `open_tabs`, restored on cold start)
- [x] Tablet two-pane explorer
- [x] Universal content thumbnail previews (photos, video frames with play badge, audio album art, PDF page 1, APK app icons)
- [x] Built-in image & video gallery viewer with image-only bottom thumbnail filmstrip, zoom/rotate, inline video playback, and "Open in another app" chooser

### Performance
- [x] Baseline + startup profile (`:baselineprofile` module, ProfileInstaller); cold start TTID 580 ms to 456 ms on API 37 emulator
- [x] Splash exits as soon as content is ready (no longer waits out the 950 ms logo animation)

### Updates and About
- [x] Auto update from GitHub releases with release notes, global dialog, channel + toggle in Settings
- [x] About screen with developer section and Acknowledgements
- [x] Background daily update check (`WorkManager` `UpdateCheckWorker` + `UpdateCheckScheduler`) and notification

---

## Next ideas (no console or API keys needed)

> Design for the 12 items below (schema v7, contracts, 5 workstreams, cut line): `development/docs/NEXT_FEATURES_DESIGN.md`.

### High value
- [x] Batch rename (pattern, counter, find/replace, preview before apply)
- [x] Secure vault (password + biometric, files encrypted at rest)
- [x] Large and old files finder (size / age filter, bulk delete to bin)
- [x] Image compress and resize as a foreground-service operation

### Network
- [ ] Folder sync / backup to a share (one-way, scheduled via WorkManager)
- [ ] Local network discovery of SMB hosts and servers
- [x] Share this phone: LAN HTTP server (NanoHTTPD) with 6-digit PIN, M3 web app (media sections, file explorer, preview, download, upload, rename, delete to Recycle Bin), live per-permission access, idle auto-stop (default 5 min), foreground service
- [ ] WebDAV provider presets (Nextcloud, Box, pCloud)

### Explorer
- [x] Text / code viewer and editor, PDF viewer, audio player
- [x] Favorites and colored tags
- [x] Compare folders, dual-pane mode on phones (`DualPaneScreen`: stacked in portrait, side by side when wide; copy / move / delete across panes; `FolderCompare` marks only-here / newer / older / different / same, "select missing")
- [x] Pinned folder shortcuts on the launcher
- [x] Recent-changes view, storage growth history graph

### Cleaner
- [x] App cache and leftover folders of uninstalled apps (`AppJunkScanner`, `QUERY_ALL_PACKAGES`; leftovers opt-in; other apps' private cache is out of reach on Android 11+)
- [x] Telegram / Instagram media cleaner (same pattern as WhatsApp)
- [x] Empty folders and zero-byte files scanner (nested empty trees, topmost folder only; empty files opt-in, dot-files kept)

### Polish
- [ ] Localization (strings already centralized in `ui`)
- [ ] Unit tests for `FileOperationExecutor` and `OperationManager`

### Needs console setup (parked)
- [ ] Cloud storage via OAuth (Drive, Dropbox, OneDrive); needs Google Cloud / Dropbox / Azure app registration and CI secrets
