# Findex Architecture

Findex is a native Android file explorer built with Kotlin, Jetpack Compose, Material 3 Expressive design, and Hilt. It is designed to support rich file operations, storage analytics, clean-up tools (including WhatsApp media cleanup), multi-format archive creation and extraction (7z, RAR, ZIP), multitasking tabs, and network storage (e.g. Windows SMB).

---

## 1. Module Architecture

The application is structured into three distinct Gradle modules adhering to clean architecture separation:

```mermaid
graph TD
    subgraph ":app"
        App[FindexApp @HiltAndroidApp]
        MainAct[MainActivity]
    end

    subgraph ":ui"
        UIStrings[res/values/strings.xml<br/>single source of strings]
        Theme[FindexTheme<br/>MaterialExpressiveTheme]
        Nav[FindexNavHost]
        Screens[Home · Explorer · Category · Tools · Settings]
        VMs[ViewModels]
        OpStrings[ResourceOperationStrings]
    end

    subgraph ":core"
        StorageRepo[StorageRepository<br/>File + MediaStore]
        LibraryRepo[LibraryRepository]
        DB[(Room / SQLite<br/>bookmarks · recent_files · trash)]
        Settings[(DataStore<br/>config only)]
        OpMgr[OperationManager]
        Clip[FileClipboard]
        Exec[FileOperationExecutor<br/>copy · move · delete · trash · restore · zip4j]
        FGS[FileOperationForegroundService]
        OpStringsIf[[OperationStrings interface]]
    end

    App --> MainAct --> Theme --> Nav --> Screens --> VMs
    VMs --> StorageRepo
    VMs --> LibraryRepo --> DB
    VMs --> Settings
    VMs --> Clip --> OpMgr
    VMs --> OpMgr
    OpMgr -- startForegroundService --> FGS
    FGS -- poll queue --> OpMgr
    FGS --> Exec
    FGS --> OpStringsIf
    OpStrings -. Hilt @Binds .-> OpStringsIf
    OpStrings --> UIStrings
```

### Module Responsibilities

| Module | Type | Purpose | Localization Rule |
| :--- | :--- | :--- | :--- |
| `:app` | `com.android.application` | Application entry point (`FindexApp` with `@HiltAndroidApp`), `MainActivity`, Android Manifest, system permissions. | **NO `strings.xml`** (pulls merged resources from `:ui`). |
| `:ui` | `com.android.library` | Compose UI components, Material 3 Expressive theming, navigation drawer, screen layouts, ViewModels. | **Centralized `strings.xml`** holding all app strings for future localization. |
| `:core` | `com.android.library` | Models, repositories, Room database, DataStore settings, operation queue, foreground service, Hilt bindings. | **NO `strings.xml`**. Notification texts come from `OperationStrings`, implemented in `:ui`. |

### Persistence rule

| Store | Holds | Never holds |
| :--- | :--- | :--- |
| Room (SQLite) `findex.db` | Bookmarks, recent files, recycle bin entries, (post-MVP) network drives | Settings |
| DataStore `settings` | Theme mode, dynamic color, hidden files, grid view, sort | Lists / records |

---

## 2. Foreground Service & Long Operations Architecture

Every copy, move, delete, clean, compress and extract runs in `FileOperationForegroundService`.
The UI never runs these on a ViewModel scope.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant VM as ViewModel
    participant Clip as FileClipboard
    participant Mgr as OperationManager
    participant FGS as FileOperationForegroundService
    participant Exec as FileOperationExecutor
    participant Notif as NotificationManager
    participant MS as MediaStore

    User->>VM: Paste / Delete / Compress / Extract
    VM->>Clip: paste(destDir) (copy or cut)
    Clip->>Mgr: enqueue(Copy | Move)
    VM->>Mgr: enqueue(Delete | Compress | Extract | Clean)
    Mgr->>FGS: startForegroundService(ACTION_START)
    FGS->>Notif: startForeground(progress notification)
    loop until queue empty
        FGS->>Mgr: poll()
        FGS->>Exec: execute(op) on Dispatchers.IO
        Exec-->>FGS: OperationProgress (throttled 120ms)
        FGS-->>Mgr: publish(ActiveOperation) → UI banner
        FGS->>Notif: update (throttled 500ms)
        Exec-->>FGS: touched paths
        FGS->>MS: scanFile(touched)
        FGS-->>Mgr: publish(OperationResult) → UI snackbar
    end
    FGS->>FGS: stopForeground + stopSelf(lastStartId)
```

**Cancel**: notification action or UI → `ACTION_CANCEL` → current job cancelled. The executor checks
cancellation every 256 KB chunk, deletes the half-written file, and zip4j tasks get `isCancelAllTasks`.

**Safety rules in the executor**
- Never overwrites: name clashes become `name (1).ext`.
- Refuses to copy/move a folder into itself.
- Move tries `renameTo` first (instant on the same volume), falls back to copy + delete.
- A failed compress deletes the partial archive.

## 3. Storage Hierarchy & Network Drive Integration

```mermaid
classDiagram
    class StorageDrive {
        +String id
        +String name
        +String path
        +Long totalBytes
        +Long freeBytes
        +DriveType type
        +Boolean isPrimary
        +Boolean isRemovable
        +Long usedBytes
        +Float usedPercentage
    }

    class DriveType {
        <<enumeration>>
        INTERNAL
        SD_CARD
        NETWORK
        USB
    }

    class StorageItem {
        +String path
        +String name
        +Long size
        +Long lastModified
        +Boolean isDirectory
        +String extension
        +String mimeType
        +Int itemCount
    }

    class FileCategory {
        <<enumeration>>
        PHOTOS
        VIDEOS
        AUDIO
        DOCUMENTS
        DOWNLOADS
        ARCHIVES
        APKS
    }

    StorageDrive --> DriveType
    StorageItem --> FileCategory
```

---

## 4. Standalone Runner (`run.sh`)

For developers without Android Studio installed, `run.sh` provides a complete headless CLI build-and-run workflow:

1. Auto-resolves `ANDROID_HOME` (`$HOME/Android/Sdk`).
2. Checks for running Android devices or emulators via `adb devices`.
3. If no emulator is running, automatically lists AVDs via `emulator -list-avds` and launches the available AVD in the background.
4. Waits for `adb wait-for-device` and polls `sys.boot_completed=1`.
5. Executes `./gradlew :app:installDebug`.
6. Launches `com.aiyu.findex.debug/com.aiyu.findex.MainActivity` with `am start`.
7. Supports `--logs` / `-l` for continuous logcat streaming.

---

## 5. Explorer State & Listing Cache

```mermaid
flowchart TD
    Nav[ExplorerRoute path] --> VM[ExplorerViewModel]
    VM -- tabs: ExplorerTab id/root/path --> State[ExplorerUiState]
    VM --> Load{load}
    Load -- cache hit --> Publish[publish items + itemsPath]
    Load -- always --> Repo[StorageRepository.getFilesInDirectory]
    Repo --> Cache[(LRU listing cache<br/>48 folders, key = path+hidden+sort)]
    Cache --> Publish
    Load -- slower than 200ms --> Spinner[isLoading = true]
    Publish --> UI[AnimatedContent keyed on tab + itemsPath]
    UI --> ScrollMap[(LazyListState per path)]
    Ops[OperationManager.results] -- refresh without spinner --> Load
    Back[PredictiveBackHandler] -- progress --> Preview[scale / slide / fade preview]
    Back -- commit --> Up[goUp → load parent from cache]
```

- **Selection** lives in `ExplorerUiState.selection` (paths). Long-press + drag selects a range
  (`dragToSelect` in `ExplorerContent.kt`) with edge auto-scroll; tap toggles while selecting.
- **Clipboard** is app-wide (`FileClipboard`), so copy in one tab and paste in another.

## 6. Tools

| Screen | ViewModel | Data |
| :--- | :--- | :--- |
| Storage Analyzer | `AnalyzerViewModel` | `StorageRepository.getCategoryStats()` + `StorageScanner.largestFiles()` |
| Storage Cleaner | `CleanerViewModel : CleanViewModel<JunkType>` | `StorageScanner.scanJunk()` → `FileOperation.Clean` via FGS |
| WhatsApp Cleaner | `WhatsAppViewModel : CleanViewModel<WhatsAppMediaType>` | `StorageScanner.scanWhatsApp()` → `FileOperation.Clean` via FGS |

`StorageScanner` skips `Android/data` and `Android/obb` and never offers top-level standard
folders (DCIM, Music…) as "empty folders".

## 7. Recycle Bin

Delete defaults to `FileOperation.Trash`. Files move (a cheap rename) into `<volume root>/.FindexTrash/` on their own volume. A `trash` row records where each one came from. `Delete permanently` and the bin screen still use `FileOperation.Delete`, which also drops matching `trash` rows.

```mermaid
flowchart LR
    Dialog[DeleteDialog<br/>Move to bin / Delete permanently] -->|Trash| Mgr[OperationManager]
    Dialog -->|Delete| Mgr
    Mgr --> FGS[FileOperationForegroundService] --> Exec[FileOperationExecutor]
    Exec -->|1. insert row| DB[(trash table<br/>batchId = operation id)]
    Exec -->|2. rename / copy+delete| Bin[/volume/.FindexTrash/]
    Snack[Undo snackbar] -->|TrashRepository.undo batchId| Mgr
    BinUI[RecycleBinScreen] -->|Restore ids / Delete trashPaths| Mgr
    App[AppViewModel init] -->|purgeExpired > 30 days| Mgr
```

- The row is inserted before the move, so a crash mid-move never leaves an untracked file in the bin.
- Deleting something that is already inside `.FindexTrash` is always permanent.
- `RecycleBinViewModel` drops rows whose file vanished outside the app.
- Room schema v2 adds `trash` through `AutoMigration(1, 2)`.

## 8. Archives

`ArchiveFormat` (core/model) detects formats by suffix, checking `.tar.gz`, `.tgz` and similar before plain extensions. It also records which formats can be written and which take a password.

```mermaid
flowchart LR
    Exec[FileOperationExecutor] -->|write ZIP| Zip4j[zip4j<br/>AES-256]
    Exec -->|write 7z / tar / tar.gz<br/>read 7z / tar.*| CC[ArchiveCodecs<br/>Commons Compress + xz]
    Exec -->|read ZIP / RAR| Native[ArchiveCodecs.readNative<br/>7-Zip-JBinding, native 7-Zip 16.02]
```

| Format | Create | Extract | Password |
|--------|--------|---------|----------|
| ZIP / ZIPX | yes (zip4j) | yes, every method: Deflate, Deflate64, BZip2, LZMA, PPMd | write AES-256, read AES + ZipCrypto |
| 7z | yes | yes | read only |
| TAR, TAR.GZ | yes | yes | — |
| TAR.XZ, TAR.BZ2 | — | yes | — |
| RAR 4 / RAR 5 | — | yes | read, including encrypted headers |

- **16 KB pages:** the 7-Zip-JBinding-4Android AAR ships 4 KB-aligned `.so` files, which Android 15+ devices with 16 KB pages refuse to load. `core/src/main/jniLibs` holds a 16 KB-aligned rebuild made by `development/scripts/build-7zip-jbinding.sh` (NDK r28). `app` uses `pickFirsts` so those copies win. Rerun the script whenever the AAR version changes.
- Every entry path goes through `ArchiveCodecs.safeTarget` (zip-slip guard). Tar symlinks and hard links are skipped.
- A failed or cancelled extract deletes the new destination folder, and a failed compress deletes the partial archive.
- `uniqueTarget` keeps multi-part suffixes whole: `a (1).tar.gz`.

---

## 9. Adaptive Large-Screen & Tablet Architecture

Findex dynamically adapts between compact phones, foldable devices, and expanded tablets:

```mermaid
flowchart TD
    WindowWidth{"Window Width"}
    WindowWidth -->|"< 600dp (Compact)"| CompactLayout["PushDrawer + Single Pane + Scaffold FAB"]
    WindowWidth -->|"600dp - 1200dp (Medium)"| MediumLayout["NavigationRail + Responsive Gutters"]
    WindowWidth -->|">= 1200dp (Expanded)"| ExpandedLayout["Permanent Drawer 300dp + Full Navigation"]

    subgraph TwoPaneExplorer ["Two-Pane Master-Detail Explorer (content width >= 720dp)"]
        ListPane["List Pane 390dp<br/>LazyList / LazyGrid<br/>Primary focus border indicator<br/>Centered Bottom FAB<br/>Selection Toolbar"]
        Divider["VerticalDivider"]
        DetailPane["ExplorerDetailPane (Inspector)<br/>Image / Video / Audio / Text Heroes<br/>Fullscreen Zoomable Lightbox<br/>In-line Audio Player<br/>Action Toolbar<br/>Properties Card + Checksums"]
        ListPane -->|"tap file: focus(path)"| DetailPane
        ListPane --- Divider --- DetailPane
        DetailPane -->|"onOpen / onShare / onDelete"| Ops["Operations / External Intent"]
    end

    MediumLayout --> TwoPane{"Content width >= 720dp?"}
    ExpandedLayout --> TwoPane
    TwoPane -->|Yes| ListPane
    TwoPane -->|Yes| DetailPane
    TwoPane -->|No| SinglePane["Single Pane Explorer"]
```

### Architectural Highlights
1. **Window-Size-Aware Navigation**:
   - `< 600dp`: Push drawer (Discord-style off-canvas sheet).
   - `600dp - 1200dp`: Persistent `NavigationRail` (`FindexNavRail`) saving horizontal space.
   - `>= 1200dp`: Permanent full navigation drawer (`FindexDrawerSheet`).
2. **Two-Pane Master-Detail Layout & Inspector**:
   - Left pane (`ExplorerListPaneWidth = 390.dp`): List or Grid of items. The Floating Action Button is centered at the bottom of the list pane with clear vertical margin, completely avoiding collision with the detail view or border. The active/focused item features an expressive tinted container and a 1.5dp primary border.
   - Middle divider: Clean `VerticalDivider` providing structural separation between panes.
   - Right pane (`ExplorerDetailPane`): Inspector surface with adaptive media heroes (`PreviewComponents`), interactive fullscreen zoom lightbox, in-line audio playback, syntax text reader with line numbers, structured specifications (dimensions, duration, exact byte size), location card with one-tap copy, and on-demand MD5 / SHA-256 checksum calculator.
3. **Readable Gutters**:
   - `readableGutter()` automatically centers content with standard margins on extra-wide screens up to `Dimens.contentMaxWidth = 840.dp`.


