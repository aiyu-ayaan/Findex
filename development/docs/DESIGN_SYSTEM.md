# Findex Design System

Material 3 Expressive on Jetpack Compose. Source of truth for every visual decision.
Implementation lives in `ui/src/main/java/com/aiyu/findex/ui/theme/`.

Inputs: the `material-3` skill (MD3 tokens, Expressive components) and the `ui-ux-pro-max`
design-system search ("file manager utility android tool productivity") which recommended a
flat, tonal, icon-forward style with a teal primary and an orange action accent.

---

## 1. Personality

| Trait | Expressed as |
|-------|--------------|
| Calm & trustworthy (it touches your files) | Teal primary, tonal surfaces, no shadows by default |
| Playful where it is safe | `MaterialShapes` icon containers, shape morph on press, wavy progress |
| Fast | Spring motion from `MotionScheme.expressive()`, no blocking animations |

---

## 2. Color

- **Dynamic color is the default on Android 12+** (`dynamicLight/DarkColorScheme`).
- Fallback brand scheme generated from seed **`#0D9488` (teal)**, tertiary from **`#EA580C` (orange)**.
- Every surface uses the tonal container ladder, never alpha-tinted `surfaceVariant`:

| Role | Use in Findex |
|------|---------------|
| `surface` | Screen background |
| `surfaceContainerLow` | List rows at rest, category tiles |
| `surfaceContainer` | Drawer, cards |
| `surfaceContainerHigh` | Storage hero card, bottom sheets |
| `surfaceContainerHighest` | Selected rows, search field |
| `primaryContainer` / `onPrimaryContainer` | Selected item, active tab, hero icon |
| `secondaryContainer` | Chips, tonal buttons, toolbar |
| `tertiaryContainer` | Cleaner / attention suggestions |
| `errorContainer` | Delete confirmation, failures |

### Category tones

Category icons need distinct hues that still follow the theme. Each category maps to a
fixed hue rendered as a container/on-container tone pair (light: tone 90/30,
dark: tone 30/90), generated with material-color-utilities and exposed as `FileCategory.tone`. Color is never the only
signal: every tile also has its icon and label.

| Category | Hue | Icon |
|----------|-----|------|
| Photos  |  Teal  |  `Image` |
| Videos  |  Orange  |  `Movie` |
| Audio  |  Purple  |  `MusicNote` |
| Documents  |  Blue  |  `Description` |
| Downloads  |  Green  |  `Download` |
| Archives  |  Amber  |  `FolderZip` |
| APKs  |  Lime  |  `Android` |

Every category uses the same `Cookie9Sided` container (`CategoryShape`) so the set reads evenly;
color and glyph tell categories apart. Pressing a tile morphs the shape into a circle.

---

## 3. Typography

System Roboto Flex (MD3 default; no bundled font). Expressive emphasis:

| Role | Use |
|------|-----|
| `displaySmall` (emphasized) | Storage percentage on hero card |
| `headlineMedium` (emphasized) | Screen titles in large app bars |
| `titleLarge` | Top app bar titles |
| `titleMedium` | Card titles, section headers |
| `bodyLarge` / `bodyMedium` | File names / metadata |
| `labelLarge` | Buttons, chips, tab labels |

Body text never below 14sp. File metadata uses `bodyMedium` + `onSurfaceVariant`.
Numbers in sizes / counts use tabular figures (`fontFeatureSettings = "tnum"`).

---

## 4. Shape

| Token | dp | Findex use |
|-------|----|-----------|
| `extraSmall` | 4 | Snackbar |
| `small` | 8 | Text fields, menus |
| `medium` | 12 | File grid cells |
| `large` | 16 | List rows (selected), FAB |
| `largeIncreased` | 20 | Category tiles |
| `extraLarge` | 28 | Bottom sheets, dialogs, drawer |
| `extraLargeIncreased` | 32 | Storage hero card |
| `extraExtraLarge` | 48 | Onboarding illustration container |
| `full` | — | Buttons, chips, toolbar, tabs |

**Shape morph**: selection check marks and category icons morph between two `MaterialShapes`
on press/selection (`Morph` + animated progress), so state change has motion meaning.

**Grouped lists** (expressive list style): consecutive rows in a section get large outer
corners (16dp) on the first/last row and small inner corners (4dp), with 2dp gaps.

---

## 5. Motion

`MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())`.

| Interaction | Spec |
|-------------|------|
| Component state (press, select, toggle) | `motionScheme.fastSpatialSpec()` (spring, slight overshoot) |
| Layout / size changes | `defaultSpatialSpec()` |
| Color / alpha | `defaultEffectsSpec()` (no overshoot) |
| Forward navigation | Shared axis X: slide 10% + fade, enter from right |
| Back / predictive back | Reverse shared axis; predictive back scales the outgoing screen to 0.9 with rounded corners while the gesture is in progress |
| Folder change inside a tab | Shared axis X (deeper = from right, up = from left). Content switches only when the new listing is ready, so there is no empty flash |
| Back to parent folder | `PredictiveBackHandler`: folder scales to 0.92, slides 32dp toward the swipe edge and fades 30% while dragging; commits on release |
| Tab switch | Fade-through |
| List first load | Staggered fade + rise, 30ms per item, max 10 items staggered |
| Slow folder (> 200 ms) | `LoadingIndicator` overlay; faster loads show no spinner |
| Sheets / dialogs | Platform defaults (slide up / scale-fade from center) |
| Progress | `LinearWavyProgressIndicator` for storage + operations, `LoadingIndicator` for loading screens |

Reduced motion: when the system animator scale is 0, Compose skips animations automatically;
stagger is also disabled when `Settings.Global.ANIMATOR_DURATION_SCALE == 0`.

---

## 6. Spacing

8dp grid with a 4dp half step. Tokens in `Dimens`:

`xxs 2 · xs 4 · sm 8 · md 12 · lg 16 · xl 24 · xxl 32 · xxxl 48`

Screen gutter 16dp on compact, 24dp on medium+. Content max width 840dp.

---

## 7. Component map

| Screen | Key components |
|--------|----------------|
| Onboarding | `MaterialShapes` morphing illustration, large filled `Button` |
| Home | `LargeFlexibleTopAppBar`-style header, connected `ToggleButton` group (Device/Network, shown once a share is saved), hero `Card` + `LinearWavyProgressIndicator`, category grid, tonal suggestion cards |
| Drawer | `PushDrawer` (Discord-style: screen slides aside as an `extraLarge` card over `surfaceContainerLow`), `NavigationDrawerItem` (full shape), `MenuIcon` two-line glyph that morphs with drawer progress |
| Explorer | `TopAppBar` + breadcrumb `LazyRow`, `SecondaryScrollableTabRow` for tabs, grouped list rows / grid cells, `HorizontalFloatingToolbar` for selection, `FloatingActionButton` (new folder) / paste `ExtendedFloatingActionButton`, `ModalBottomSheet` for item actions and sort |
| Dialogs | `AlertDialog` with `OutlinedTextField`, password field with visibility toggle |
| Operations | Banner card with `LinearWavyProgressIndicator`, notification with progress |
| Tools | Donut chart (Canvas), grouped lists with checkboxes, `ExtendedFloatingActionButton` clean action |
| Settings | Grouped list rows with `Switch`, `ButtonGroup` toggle for theme mode |

---

## 8. Selection gestures

Matches Google Photos:

1. **Long-press** an item → selection mode starts, item selected, haptic `LongPress`.
2. **Keep dragging** after long-press → every item between the anchor and the finger is
   selected (range select). Moving back shrinks the range. Near the top/bottom edge the
   list auto-scrolls.
3. In selection mode, **tap** toggles single items.
4. Back gesture / close button clears selection.
5. A `HorizontalFloatingToolbar` slides up with Cut, Copy, Compress, Delete, Select all and an
   overflow for Rename / Details / Share.

Every gesture has a button alternative (select all, per-row checkbox).

---

## 9. Accessibility checklist

- Touch targets ≥ 48dp, 8dp between targets.
- Contrast: `onX` on `X` pairs only; metadata text uses `onSurfaceVariant` on `surface*`.
- Icon buttons have `contentDescription`; decorative icons next to text use `null`.
- Selection state exposed via `Modifier.toggleable` / `selected` semantics.
- Dynamic text: rows wrap to two lines, no fixed heights on text containers.

---

## 9b. Brand mark & splash

The mark is an **F built from list rows**; the orange dot after the second row is the item you found. Masters live in
`development/docs/brand/` (`logo-symbol.svg`, `logo-mono.svg`, `app-icon.svg`), drawn on a 256 grid with 40-unit strokes
and 20-unit round caps.

| Use | Mark | Accent | Background |
|---|---|---|---|
| Launcher (adaptive) | `#065C54` | `#EA580C` | `#A9F0E5` |
| Themed / monochrome icon | white | white | system |
| Splash light | `#1E6A62` (primary) | `#EA580C` | `#F6FAF8` (surface) |
| Splash dark | `#9CD1C8` (primary) | `#FF9D76` (tertiary) | `#0A0F0E` (surface) |

- Keep the dot separate from the second row; never recolour the F orange or the dot teal.
- Splash (API 31+, `drawable-v31/splash_icon.xml`, 950 ms): the stem grows up from a dot, both rows slide out of it
  (M3 emphasized decelerate), then the dot springs in (`expressive_spatial`, one small overshoot). Paths morph with matching
  commands so the round caps never stretch. Older versions show the static `drawable/splash_icon.xml`.
- Exit: after the icon animation finishes, the icon and background fade together while the icon scales to 0.9
  (200 ms, emphasized accelerate). The icon has to fade on its own because it is a separate surface on API 31+.

---

## 10. Large-Screen & Tablet Design

- **Navigation**:
  - Modal push drawer for compact widths (< 600dp).
  - Navigation Rail (`surfaceContainerLow`, 80dp) for medium widths (600dp–1200dp).
  - Permanent Navigation Drawer (`surfaceContainerLow`, 300dp) for expanded widths (>= 1200dp).
- **Two-Pane Explorer & Inspector**:
  - Activated when available content width is >= 720dp.
  - List pane takes the remaining width (`weight(1f)`), with centered bottom FAB for paste/create actions and subtle `VerticalDivider` separating panes.
  - Focused item in list/grid is highlighted with `secondaryContainer` and a 1.5dp primary accent border.
  - Detail pane: MD3 supporting pane, fixed `ExplorerDetailPaneWidth = 360.dp`; `surfaceContainerLow` surface with 28dp corners, 1dp subtle border.
  - Inspector order: category chip + close → hero (capped: image ≤ 220dp, video ≤ 200dp, text ≤ 180dp, icon heroes 56dp) → name + size/date → one full-width primary button (Open / Open folder / Extract) → icon toolbar (share, copy, cut, rename, compress|open, delete) → Overview card with label/value rows + location → checksums.
  - Hero cards carry no action buttons; the primary button is the single CTA.
  - Media hero previews (`PreviewComponents`):
    - **Photos**: Adaptive framing with resolution (`1920 × 1080 · 2.1 MP`) and size chips, fullscreen button, and interactive Fullscreen Lightbox Dialog featuring pinch-to-zoom and pan gestures.
    - **Videos**: 16:9 responsive card with video frame thumbnail, gradient scrim, duration chip, and centered play button.
    - **Audio**: In-line player with real-time playback control, track slider, metadata (title, artist, album), and expressive category tone vinyl styling.
    - **Code/Text**: Code card with line numbers gutter, copy content action, and monospace syntax styling.
    - **APKs**: Application icon, label, package name, version.
    - **Archives**: Format badge (ZIP/7Z/RAR) and size.
    - **Folders**: Subfolder/file count, total computed size, and direct Open Folder navigation.
  - **Action Toolbar & Properties**:
    - Primary CTA (Open/Extract/Install) paired with Share, followed by a secondary tonal icon strip (Copy, Cut, Rename, Compress/Extract, Delete).
    - Technical Specifications card: Formatted size + exact bytes, MIME type, modified timestamp, dimensions, duration.
    - Location card: Folder path with copy-to-clipboard action.
    - Checksum card: On-demand MD5 and SHA-256 calculator with one-tap copy.


