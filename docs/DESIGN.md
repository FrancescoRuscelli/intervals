# Intervals: design decisions and plan

This file is the source of truth for what was agreed so far (2026-11-16).
Read it before continuing work on another PC or in a new session.

Mockups (all screens, dark + light): https://claude.ai/artifact/9xv7NeynQZCu85moHtEfav
A copy of the mockup source is in `docs/mockups/` (open the `.dc.html` files on the canvas link above to see them rendered).

## What the app is

An Android interval timer, replacing **Hybrid Interval Timer** (`com.hybrid.intervaltimer`), for hangboard / HIIT workouts.
Target: Pixel 8a. UI language: English. Native Android (Kotlin + Jetpack Compose).

## Workout model

- **Workout**: name, tags, warm-up, rounds, **rest between rounds**, list of blocks.
- **No cool-down** (removed on purpose).
- **Rest between rounds** is played only *between* rounds, never after the last one
  (the old app's final rest was useless and inflated the total).
- **Block** = a single series, or a **repeat group** ("Repeat ×N" of a few series).
- **Series**: name, duration, color (12 Material colors from the old app), icon
  (jump, run, stand, weight, warm-up, cool), optional **per side** (left → change N s → right).

Implemented in `core/` (`Model.kt`, `Timeline.kt`), with tests.

### Logged fields (agreed 2026-11-17, not implemented yet)

- A series can **log values**: a list of fields the user defines (name + unit), e.g. **Load kg**, **Edge mm**.
- A field can be **left and right apart** (one value per side, e.g. Load L +10 / R +8), else one value for both.
- Values start from the last session, can be changed **in the rest between rounds** (timer keeps running)
  and are confirmed on the **Finished** screen. Saved **per round** in the journal entry.
- Interval series are **not linked** to a FitNotes-style exercise library (different in nature);
  a manual strength log (FitNotes-like) in the same journal is an idea for later.

## Import from the old app

CSV backup, no header, no quoting, two row types (see `HybridImport.kt`):
- workout: `id,name,warmupSec,cooldownSec,totalSec,rounds`
- interval: `id,workoutId,name,durationSec,,,0,icon,color(signed ARGB int)`

Rules:
- If the last series of a workout is named *rest* / *change*, it becomes **rest between rounds** (19 of the 26 workouts; ~31 min of useless rest removed in total).
- Repeated patterns become **repeat groups** automatically (e.g. hang/rest ×3 + hang).
- Cool-down column is ignored (always 0 in the real export).
- Verified on the real export: 26 workouts, every new total = old total − final rest.
  Run: `./gradlew :core:test -Pintervals.csv=/path/to/tutto_hiit_hybridInterval.csv`
  (the real export is NOT in the repo; a 4-workout sample is in `core/src/test/resources`).

## Screens (see mockups)

Bottom bar: **Workouts · Journal · Stats · Settings**.

1. **Workouts list**: search, tag filter chips, cards with name, total, mini colored bar of one round, play button, "New workout".
2. **Edit workout**: name, tags, warm-up, rounds (− / +), rest between rounds, one-round and total time, series list with repeat groups.
   Swipe a series to delete; long-press to select → *Repeat ×…*, *Duplicate*, *Delete*. Quick add *Work* / *Rest*.
   Duplicate a whole workout (from the list menu).
3. **Edit series**: header in the series color, name, big duration with ± , color, icon, **per side** (change time), **Log** (own fields, left/right apart, add field), sound switches.
4. **Player** (chosen: **big concentric disc**):
   outer ring = series of the current round as segments sized by length (done = grey, current thicker, dot marker,
   rest between rounds = thin teal arc); **inner thin ring = the rounds** (done = grey, left = purple, the current one
   empties as it runs); inner disc filled with the series color showing icon, name and the time.
   All ring segments have **rounded ends** (gaps kept the same width).
   **Last 3 seconds**: the time becomes a big 3 / 2 / 1; each number starts huge and higher up and drops down,
   shrinking to its size in ~0.4 s (also for the 3-2-1 "Get ready"). Live demo: 4c on the canvas.
   **New series**: the disc inflates from the center, fast (~0.25 s, tiny overshoot), with its text.
   **Disc animation** setting: **Calm** (default, demo 4c) or **Crazy spin** (demo 4d): at every new series the disc with its name and time whirls
   1.5 turns and settles (~0.7 s), the series ring swings back and the rounds ring the other way. No always-spinning mode. Demo: 4d.
   **Workout end** (both settings): the disc flips like a tossed coin around its vertical axis (~3.5 turns, ~1.4 s,
   lifts a little mid-flip) and lands on its back: purple, "done ✓". Then the Finished screen.
   Round x/y, remaining, series x/y, "Next" card, whole-workout progress bar, previous / pause / next.
   **Landscape layout** for viewing from far away. **3-2-1 countdown** before start. **No color flash** (removed).
   **No extra disc animations** in v1; only the "Liquid / bubbles" demo is kept on the canvas, maybe later.
   **Rest between rounds**: countdown plus − / + for the logged fields of the next round (4b on the canvas).
5. **Import**: file card, summary, list with new total and old total struck through, "Import N workouts".
6. **Journal**: month calendar (Monday first), dots per session, month totals, sessions of the selected day
   (time, length, completed / stopped in round x, **note**). Sessions are logged automatically, with their logged field values (load, edge…). No manual sessions, no RPE.
7. **Stats**: Week / Month / Year; time, sessions, completed; minutes per week chart; most done workouts.
8. **Settings**: theme (System / Light / Dark), accent color (purple default), voice names + language, beeps, volume,
   lower music during cues, 3-2-1 countdown, disc animation (Calm / Crazy spin), keep screen on, lock controls; import, **backup to file**, **restore**.
9. **Finished**: completed disc, time / rounds / series, **logged values to confirm** (per side, *Set per round*), note for the journal, *Repeat*, *Save to journal*.

Style: **pure black** backgrounds in dark mode (OLED), Space Grotesk-like numbers, and a four-color palette (agreed 2026-11-17, "Palette" board):

| Color | Hex | Role |
|---|---|---|
| Purple | `#7C3AED` (light `#6D28D9`) | The app: buttons, selected tabs/chips, rounds left, this week in charts |
| Pink | `#E91E63` | Work: default for new work series, minutes in Stats, completed sessions and journal dots |
| Yellow | `#FFC107` | Rest: default for new rest series, stopped sessions (fill only in light mode, with dark text) |
| Teal | `#00BFA5` (light text/icons `#00897B`) | Breaks: warm-up, rest between rounds, change side, get ready; second series in charts |

The series color picker shows these four first, then the other Material colors.

Not wanted (for now): sharing workouts, reminders, home-screen widget, smartwatch, Health Connect.

## Gym: strength log (mockups G1–G8, agreed so far 2026-11-17, not implemented)

Replaces FitNotes. Same look as the rest of the app, as simple as possible.
- **Placement: own Gym tab** (G1). Tabs: Intervals · Gym · Journal · Stats · Settings. (G1b switch rejected.)
- **No preset list, no categories**: you create your own exercises.
- **Exercise = name + what you write down**: weight, reps, time, distance, or your own field.
  - Weight can be **+ added** or **− assist** (e.g. Deloaded One Arm Pull-Up: weight taken off).
  - **Left and right apart**: one exercise, each set is L or R; "Both" logs one set per side with the same values;
    after a set it switches side. Never separate "(Left)" / "(Right)" exercises.
  - **Bands** (kept simple, not used often): one row of color dots on the log screen, tap to add, tap again for ×2;
    shown strongest → lightest (green, purple, black, red, yellow). No band picker screen, no bands graph or records.
  - **Rest clock counts up** (not a countdown), starts when a set is logged. Each exercise has its own rest target:
    at the target it buzzes / notifies, then keeps counting so you see how long you really rested.
    The real rest before each set is saved; shown on the set ("rested 3:25"), in history and in Stats (average vs target).
- **Edit exercise**: rename (history kept), notes, fields, **step for − / +** per field (weight 0.5 / 1 / 1.25 / 2.5 / 5 kg,
  default 2.5, imported from FitNotes), left/right, rest, groups; **merge** another exercise into it (backup first); delete (asks first).
- **Log screen (G2, agreed clean style)**: header shows last time; text tabs with underline; big tiles side by side
  (weight | reps, big − / +); one "Log set" button + note button; small teal rest pill; sets as plain rows
  (number, value, rest on the right), tap a row to edit / add a note. Bands = full-width row of dots.
- **Quick notes per set**: tags (Max, Bad form, Assisted, Failed, +½ rep, your own) + free note.
- **Variants stay separate exercises** (Block Pulls Squeezed, Pull Up (Second Half)…).
- **Groups** of exercises (Pull day, Legs…), started from Today.
- Per exercise: **Log · History · Graph · Records** (History = every workout, newest first, tap to edit) (graphs and records as in FitNotes, only those that fit the exercise):
  - Graphs per workout: estimated 1RM (Epley, sets ≤ 12 reps), max weight, volume, total reps, max reps,
    weight for N reps, max time / distance; plus ours: left vs right on one graph, real rest vs target.
    Periods 3 M / 6 M / 1 Y / All, trend line, tap a point to open that workout; chosen graph remembered per exercise.
  - Records: real heaviest weight per rep count (beaten ones faded) + estimated 2–10RM from the best 1RM.
- **Import from FitNotes**: from the `.fitnotes` backup (SQLite; the CSV export has no comments).
  - Merge "(Left)"/"(Right)" pairs into one per-side exercise (11 pairs; one-side-only ones become per-side too).
  - Comments become data: "L"/"R" → side, band emojis (🟢🟣🔴⚫🟡, "nero", "green+purple"…) → bands,
    the rest kept as notes **exactly as written (Italian)**.
  - Suggest groups from exercises done on the same day; skip unused exercises and categories; recompute records.
- Not wanted (from your FitNotes data, never used): routines, body weight, measurements, goals, plate calculator.

## Energy efficiency (agreed goal: very efficient)

- Pure black in dark mode; screen kept on only during a workout.
- No polling: the timer sleeps until the next event (series change, beep, voice); based on `SystemClock.elapsedRealtime`.
- Foreground service + partial wake lock only while a workout runs; released on pause / finish; long pause auto-stops.
- Voice lines pre-synthesized at start (TextToSpeech → files), SoundPool for beeps, audio focus "duck".
- Redraw only the disc; ~1 update per second when no animation.
- No network, no analytics, no background jobs. Measure with Android Studio Energy Profiler.

## Tech stack

- Kotlin 2.4, Jetpack Compose (BOM 2026.09.00), Material 3, AGP 9.5 (built-in Kotlin), Gradle 9.9 wrapper.
- `core/`: plain Kotlin/JVM (model, timeline, importer), unit-tested.
- `app/`: Android, minSdk 26, compile/target 37.
- Planned: Room (database), foreground service (timer), TextToSpeech, SoundPool, Storage Access Framework (import / backup JSON).

## Plan / status

- [x] Mockups agreed
- [x] Step 2: project setup, data model, timeline, CSV importer + tests (all passing)
- [x] Mockups for logged fields (series editor, rest between rounds, finished)
- [x] Player details: rounds ring, rounded segments, last 3 s drop-in, no flash; palette (purple, pink, yellow, teal)
- [x] Step 3: Room database + repository (with logged fields per round and side); backup / restore JSON
  - `core/`: `LogField` on a series (+ say name / beep switches), `Session` + `LoggedValue` (`Journal.kt`),
    `AppSettings` (`Settings.kt`), backup format `intervals-backup` v1 (`Backup.kt`; unknown keys ignored, newer version refused)
  - `app/data/`: Room tables workouts (content as JSON), sessions, logged_values, settings; schema in `app/schemas/`;
    `IntervalsRepository` is the only way the screens touch data (add / edit / duplicate below / reorder / delete workouts,
    import, sessions per month, notes, last values for the next session, settings, export / restore backup).
    Deleting a workout keeps its sessions (unlinked, name kept). A bad backup file changes nothing.
  - Tests: 16 in `core`, 8 database tests in `app` (Robolectric, run on the PC)
- [ ] Step 4: screens (list, editor, series, import, journal, stats, settings, finished)
- [ ] Step 5: player screen + foreground timer service, voice, beeps, countdown, flash, landscape
- [ ] Step 6: install on the Pixel 8a, real workout test, energy profiling
- [x] Gym mockups G1–G16 (placement: own tab)
- [ ] Gym: data model, database, screens, FitNotes importer (after the interval screens? to decide)
- [ ] App icon (to decide: e.g. purple disc with segmented ring)
