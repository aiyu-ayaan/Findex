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
fixed hue that is **harmonized toward `primary`** at runtime and rendered as a
container/on-container pair (light: tone 90/10, dark: tone 30/90). Color is never the only
signal: every tile also has its icon and label.

| Category | Hue | Icon | Shape |
|----------|-----|------|-------|
| Photos | Teal | `Image` | `Cookie9Sided` |
| Videos | Orange | `Movie` | `Clover4Leaf` |
| Audio | Purple | `MusicNote` | `Sunny` |
| Documents | Blue | `Description` | `Square` (rounded) |
| Downloads | Green | `Download` | `Pill`-like `Arch` |
| Archives | Amber | `FolderZip` | `Gem` |
| APKs | Lime | `Android` | `Cookie6Sided` |

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
| Folder change inside a tab | Shared axis X (deeper = from right, up = from left) |
| Tab switch | Fade-through |
| List first load | Staggered fade + rise, 30ms per item, max 10 items staggered |
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
| Home | `LargeFlexibleTopAppBar`-style header, `PrimaryTabRow` (Device/Network), hero `Card` + `LinearWavyProgressIndicator`, category grid, tonal suggestion cards |
| Drawer | `ModalDrawerSheet`, `NavigationDrawerItem` (full shape) |
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
