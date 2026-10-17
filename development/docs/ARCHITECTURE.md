# Findex Architecture

Findex is a native Android file explorer built with Kotlin, Jetpack Compose, Material 3 Expressive design, and Hilt. It is designed to support rich file operations, storage analytics, clean-up tools (including WhatsApp media cleanup), multi-format archive creation and extraction (7z, RAR, ZIP), multitasking tabs, and network storage (e.g. Windows SMB).

---

## 1. Module Architecture

The application is structured into three distinct Gradle modules adhering to clean architecture separation:

```mermaid
graph TD
    subgraph ":app"
        App[FindexApp Application]
        MainAct[MainActivity]
        AppNav[App Navigation Entry]
    end

    subgraph ":ui"
        UIStrings[res/values/strings.xml<br/>Single Source of Truth]
        Theme[Material 3 Expressive Theme]
        Screens[HomeScreen, Explorer, Cleaners]
        Components[StorageCard, CategoryGrid, Drawer, Dialogs]
        VMs[HomeViewModel, ExplorerViewModel]
    end

    subgraph ":core"
        Repo[StorageRepository & FileRepository]
        Models[StorageDrive, StorageItem, FileCategory, FileOperation]
        FGS[FileOperationForegroundService]
        DI[CoreModule / Hilt DI]
    end

    App --> UIStrings
    App --> Theme
    App --> Screens
    App --> Repo
    App --> FGS
    Screens --> VMs
    VMs --> Repo
    Screens --> Components
    Components --> Models
    Repo --> Models
    FGS --> Models
```

### Module Responsibilities

| Module | Type | Purpose | Localization Rule |
| :--- | :--- | :--- | :--- |
| `:app` | `com.android.application` | Application entry point (`FindexApp` with `@HiltAndroidApp`), `MainActivity`, Android Manifest, system permissions. | **NO `strings.xml`** (pulls merged resources from `:ui`). |
| `:ui` | `com.android.library` | Compose UI components, Material 3 Expressive theming, navigation drawer, screen layouts, ViewModels. | **Centralized `strings.xml`** holding all app strings for future localization. |
| `:core` | `com.android.library` | Models, repositories, storage queries, foreground services for background tasks, Hilt bindings. | **NO `strings.xml`**. |

---

## 2. Foreground Service & Long Operations Architecture

Long-running tasks such as batch file copying, moving, archiving, extraction, and junk cleaning run within `FileOperationForegroundService` with notification channel support.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as Compose UI / ViewModel
    participant Repo as Storage / File Repository
    participant FGS as FileOperationForegroundService
    participant Notif as Android NotificationManager
    participant FS as File System / Storage Access

    User->>UI: Trigger operation (e.g., Copy, Archive, Clean)
    UI->>FGS: Start Service with Operation Intent
    FGS->>Notif: Post ongoing foreground notification (progress)
    loop Processing files
        FGS->>Repo: Execute batch chunk
        Repo->>FS: Perform I/O operation
        FS-->>Repo: Operation status
        Repo-->>FGS: Progress update (bytes / item count)
        FGS->>Notif: Update notification progress fraction
        FGS-->>UI: Emit StateFlow progress
    end
    FGS->>Notif: Show completion notification
    FGS->>FGS: stopForeground() & stopSelf()
    FGS-->>UI: Operation Complete
```

---

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
