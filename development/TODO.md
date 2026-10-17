# Findex Development Roadmap & Tasks

Track progress and implementation milestones for Findex file explorer.

---

## Phase 1: Project Initialization & Core Architecture
- [x] Multi-module Gradle configuration (`app`, `core`, `ui`)
- [x] Kotlin 2.4 + AGP 9.4 with Java 17/21 compatibility
- [x] Hilt dependency injection setup across all modules
- [x] Material 3 Expressive theme (`FindexTheme`, dynamic color, typography, expressive tokens)
- [x] Centralized localization strategy (`ui/src/main/res/values/strings.xml` as single source; no strings in `app` or `core`)
- [x] Storage models (`StorageDrive`, `StorageItem`, `DriveType`, `FileCategory`, `FileOperation`)
- [x] Foreground Service skeleton (`FileOperationForegroundService`) for background tasks
- [x] Home screen UI:
  - [x] Storage drive cards (Internal & SD Card with animated progress)
  - [x] Dynamic tab section (Device / Network; Network tab conditionally visible)
  - [x] Quick category tiles (Photos, Videos, Audio, Documents, Downloads, Archives)
  - [x] Quick tools & suggestions cards (Storage Cleaner, WhatsApp Cleaner, Storage Graph)
  - [x] Modal navigation drawer with navigation routes
- [x] Standalone `run.sh` runner script (emulator auto-boot, install, launch, logcat streaming)
- [x] Verified build and execution on Android 17 emulator

---

## Phase 2: File Browser & Directory Navigation
- [ ] Directory content listing with thumbnail loading
- [ ] Breadcrumb navigation and path jump
- [ ] File sorting (Name, Date, Size, Type) and view modes (List / Grid)
- [ ] Predictive back-gesture support and navigation transitions

---

## Phase 3: File Operations & Foreground Task Engine
- [ ] File copy & move with chunked background streaming
- [ ] Safe batch deletion with trash / recycle bin support
- [ ] File rename and directory creation
- [ ] Real-time notification progress reporting in `FileOperationForegroundService`

---

## Phase 4: Archive Management
- [ ] Multi-format extraction (ZIP, 7z, RAR)
- [ ] Archive creation (ZIP, 7z, TAR)
- [ ] Password-protected archives encryption/decryption
- [ ] In-archive file preview without full extraction

---

## Phase 5: Storage Analytics & Cleaners
- [ ] Storage Graph visualization (pie chart / breakdown of space consumption)
- [ ] System cache & junk file scanner
- [ ] WhatsApp Cleaner (sent media, voice notes, stickers, database backups)
- [ ] Duplicate file finder and large file scanner

---

## Phase 6: Multitasking & Multi-Tab Support
- [ ] Tab strip for concurrent directory browsing
- [ ] Drag-and-drop / cross-tab copy & paste
- [ ] Tab session persistence

---

## Phase 7: Network Storage
- [ ] Windows Network Share (SMB / Samba) client
- [ ] WebDAV and FTP / SFTP protocol connections
- [ ] Network drive credential manager & bookmarking

---

## Phase 8: Gestures & Multi-Selection
- [ ] 3D touch / Long-press select mode
- [ ] Drag-to-select range (Google Photos-like swipe selection)
- [ ] Contextual floating action toolbar for selected items
