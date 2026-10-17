# Findex

Fidex is the file explorer is an android app, build on top on material-3-expressiove. It features all stuff needed for file explorer like have storage graph, storage cleaner,
whatsapp cleaner, all time or archive extractor (7zip,rar,zip) and also have abitilty to create archive but with extra features like tab support to multitasking. 
Multitasking can be help in copy pasting. Also have option to connect to network storage same like windows.. 


## Stact 

Build with native android so kotlin

Package name will be `com.aiyu.findex`

App will have 3 modules 

- app => Entry point of the application
- core => will have all db or core releated stuff 
- ui => ui related stuff.. 

Note ui module have all the string.xml no core or app have string.xml, its because in future want to make application localalize, ui will hold all the strings.

## Design design 

- On Start it will use the `hilt` for dependencies injection. 
- For any long task it will use the foreground service like copy pasting or unzipping or zipping task or cleaning task.
- Ui will be mordern and simple with homescreen will have a tab section with tab like device or network. Network will only be visible when their is network drive.
- Ui will have a drawer where all the other features exist.. 
- Home scection will have a card to show the storage drive like internal or sd card if present and all suggection like clean and all and also option for commman media 
formate like photo, videos, documents. 
- Also make sure you bottom sheets for only pages where it need to be like scope wize.. 
- On File long press it will have all the basic features like cut,copy,details and many more. Compress will be also and option, with options like all formate available and 
also able to add password to it.
- Also user will have 3d touch features to selected multiple times and mulitple items can be selected on longpress like if user swipe it will selected those items or user longpress 
on one item and then click items individuall both will do the same  select process same how google photos have it.
- Long press to select will have a toolbar menu items.
- UI should feel fully native to android app with material3 expressive animation,predective back-gestures, material3 expressive motions



## AI Agents 

- Never add yourself as co-auther in any project.
- Commit message will able have the `scope(Change) : Message` structure
- @Development folder is for you. Add all the docs over their in @docs folder and for any tracking task I have created a @Development/TODO.md file track over their or create a new one if needed.
- You are allow to update the claude.md file or any other file releated to ai brain.
- Always use `codegraph` to scan the code. 
- For achitecture always or any changes make sure always add a achitecture diagram in docs.
- This app targets the most efficient home automaction solution.

## Working Docs (read before UI or architecture work)

- `development/docs/MVP_PLAN.md` — MVP scope, milestones (M0–M8), key decisions, post-MVP list.
- `development/docs/DESIGN_SYSTEM.md` — colors, shapes, motion, spacing, selection gestures. All UI must follow it.
- `development/docs/ARCHITECTURE.md` — module + flow diagrams. Update the diagrams with every structural change.
- `development/TODO.md` — tick items as milestones land.

## Conventions

- Compose only, `androidx.compose.material3` 1.5 alpha (Expressive APIs; opt-ins are set in Gradle).
- Colors only through `MaterialTheme.colorScheme` roles; category colors through `CategoryTones`. No raw `Color(0x…)` in screens.
- Spacing through `Dimens`; shapes through `MaterialTheme.shapes` / `MaterialShapes`.
- Motion through `MaterialTheme.motionScheme` specs, never ad-hoc `tween` durations.
- Persistence: app data in SQLite through Room (`core/db`). DataStore is only for config/settings. Never store lists or records in DataStore.
- Long file work goes through `OperationManager` → `FileOperationForegroundService`. Never run copy/move/delete/zip on a ViewModel scope directly.
- Commit at each milestone: `scope(Change) : Message`, e.g. `ui(Explorer) : Add drag-to-select`.
- Build check: `./gradlew :app:assembleDebug`. Run on emulator: `./run.sh`.
