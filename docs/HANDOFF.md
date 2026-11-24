# Intervals handoff

Updated 2026-11-23. Read [CLAUDE.md](../CLAUDE.md) for repo conventions and [DESIGN.md](DESIGN.md) for design history. The current navigation decision is recorded here.

## Merge of both PCs: done on 2026-11-24 (no read-tree needed)

The steps written on the other PC (make `8fadd24` the content of `main`, then cherry-pick) are **obsolete, do not run them**.
This PC already had a full day of new work on top of `962b73a`, so instead its uncommitted work was stashed,
`main` was fast-forwarded to `55321e0`, and the work was put back on top.

- **From the safety branch `8fadd24`.** Only Settings › "Back up and restore" / "Import from another app" and Merge exercises
  were missing, and they were brought back by hand. Its Stats is older than the Stats screen rebuilt here (sections below), and its
  database is v7 while `main` is v8, so nothing else is taken from it. Delete the local branch once the user agrees.
- **From the other PC (24 Nov).** The variant row and Note · Delete · Undo at the bottom are kept. Its L / R rounds (`SideRounds`, `sideRounds`)
  were removed: compared in [the rounds mockup](mockups/intervals-side-rounds-compare.html), the user chose this PC's U rounds in `SetBlocks`.
- **The Journal table fix (cut-off Pinch label) is moot:** the table by load was removed here, only the bars are left.
- Conflicts were only `JournalScreen.kt` (the removed table) and `GymRowsTest.kt` (the `loadRows` test dropped, `sideRounds` kept).

**Back button (2026-11-24).** On Intervals or Gym with nothing open on top, Back goes to the Journal; only Back on the Journal leaves the app.

**Move sets to another exercise (2026-11-24, [mockup](mockups/intervals-move-sets.html), C).** On the Gym day page, hold an exercise
(its name or a set), then **Move sets…**: a sheet with that exercise's sets of the day, all ticked (tap one to keep it), the exercise to
move them to (similar names first, search) and its variant. A yellow line says when the target lacks a field the sets have.
The sets keep day, place, rest and note; the snackbar offers Undo (`moveSetsTo` / `putBackSets`, tested in `GymImportTest`).
A whole exercise is still merged from Edit exercise › Merge another exercise into this.

**Records: best now and beaten (2026-11-24, [mockup](mockups/intervals-record-marks.html), A).** A record's cup on the Journal and
History bars is gold while it is still the best (per side), grey once a later day beat it. Tapping the bar in the Journal reads
"PR, best now" or "was a PR until 7 Jul" (`recordMarksOn` in `GymGroups.kt`, tested on Pinch's real numbers).

## Ideas from other apps (2026-11-24, [mockup](mockups/intervals-ideas-from-other-apps.html))

Chosen: 1, 2, 3, 4, 5, 6, 9, 11, 12, 13. Not now: 7 (do this day again), 8 (finger load: climbing itself is not logged, so the
number would be wrong exactly on climbing weeks), 10 (climbing log: grades too varied). A later idea for 8 + 10: a plain "Climb"
entry with only minutes and easy / hard.

- **1 Load hint.** Gym log: when the last two days (same side, variant; warm-ups out) had the same top weight, the reps held and no
  set was tagged Failed, a teal "↑ Try 17 kg" under the tiles; tap = in the tile; gone after a set at that load (`nextLoadHint`).
  Intervals: a kg value equal in the last two sessions, both completed with nothing let go → "try +1 kg" on the card, Setup
  (tap to use) and Finished (`intervalLoadHints`, step `INTERVAL_LOAD_STEP` = 1 kg).
- **2 Let go.** In the player, a Let go button between previous / next during work series (not rest / change / round rest); tap
  again to take it back; the timer keeps going. Finished shows "hang 11 / 12" with dots; the Journal "hang 11/12 · let go in round 2".
  `Session.letGo` + `workDone` (database v9, columns `letGo`, `workDone`; in backups).
- **3 Progress.** `WorkoutProgressScreen`: a chip per logged number, periods, left pink / right teal, records gold / grey
  (`valueSeries`, `valueRecords`). Opened from a Journal session ("Progress ›") or the list's selection bar (one workout selected).
- **4 Rest notification.** Shows the next set ("set 4 · 90 × 5", "L 16 × 3"), +30 s, Stop; a tap opens that exercise. The rest is
  kept in SharedPreferences (`RestAlarm.saved`), so a rest across an app restart still times and saves.
- **5 Warm-up.** First quick tag; such sets are dashed outline bars and are left out of records, graphs, repMaxes, Most trained and
  the set counts ("3 sets + 2 warm-up") (`isWarmup`).
- **6 Records per variant and spec.** Cups, PR counts and recent records compare a set only with sets of the same variant and
  specs (a missing single choice = the first choice) (`recordKey`). History / Graph / Records filter chips include "grip: thumb".
- **9 Choices and values on the disc.** A series log field can be "A choice" (Grip: open / half crimp / full crimp), saved as
  `LoggedValue.text` (database v9). The disc shows this side's values ("20 mm · half crimp · 10 kg").
- **11 Landscape.** The player lays out the disc left, the rest right when the phone is on its side.
- **12 Rest between rounds.** The next round's values with − / + (choices as chips) under the disc; each round is saved with the
  values it was done with (`PlayerController.perRound`); a value changed on Finished applies to all rounds.
- **13 Swipe to delete** a series in Edit workout, with Undo above the list for 6 s.

The phone's database (v8) was opened with v9 from a copy before installing: 26 workouts and 2 sessions kept.

**Tags in the Gym list (2026-11-24, [mockup](mockups/intervals-gym-list-tags.html), B).** The Gym tab's exercise list and Add exercise
show each exercise's tags under its name: a dot in the tag's Journal color, then the name in grey ("● Legs   ● Pull"), cut with …
when they don't fit (`exerciseTags` in `GymPickScreen.kt`). The Gym list's search also finds tags.

**Merge keeps a variant (2026-11-24).** Merging into an exercise without variants lost what told the sets apart (Box Pike Push Up into
Pike Push Up). Now the merge dialog offers "Keep them apart as a variant" (on when the names differ): the new variant is named from the
missing words (`variantSuggestion`: "box"), the target's own sets get "normal"; into an exercise with variants, "+ box" is offered next
to the existing ones. The Edit exercise screen adds the new variant to its own copy too, else Save afterwards removed it again (happened
with Parallettes Pike Push Up: the 81 sets kept "parallettes", the list lost it; fixed by hand). The phone's Pike Push Up was fixed by hand: variants normal (200 sets) / box (56 sets, 10 Sep – 19 Nov).

**Keyboard and Back (2026-11-24).** Opening a screen or switching tab clears focus and hides the keyboard (the Gym search kept it up
under an exercise's log). With the keyboard up, Back only hides it: an App-level BackHandler keyed on `isImeVisible` is added last,
so it comes before the screens' own Back.

**Taps through screens (2026-11-24).** A screen on top of the tabs (an exercise's log, Settings…) let a tap on its empty space reach the
tab underneath: missing the log screen's pencil opened the Gym tab's ⚙ Settings. App.kt now puts a touch-catching layer under every
overlay.

**Variants with their own fields (2026-11-24, [mockup](mockups/intervals-variant-fields.html), 1 + 2A + 3).** "What you write down" is now
"Each set records". A variant can record other fields than the exercise (`Exercise.variantFields`, `fieldsOf` / `forVariant` / `forSet`):
Edit exercise shows "records: …" under each variant, tap = a dialog with the chips and "Same as the exercise". Set texts, boxes and bars
follow each set's variant; the log's tiles and timer follow the chosen variant; Graph and Records use the chosen variant's fields, and
"All" only what every variant records (`commonFields`). Records are counted per variant with its own main graph (`mainMetric`). On a
per-side exercise, sets without a side get their own records and graph line ("No side", `sidesOf` / `NoSide`). Merge: a new variant takes
the merged exercise's fields when they differ, and its specifications (e.g. Bands) come along.
On the phone (by hand, backup `before-hold.db`): One Arm Pull Up (made by the user from 4 merges) got 90° hold = weight + time,
resistance band = reps, and the Bands spec back (28 sets had bands).

**Variant groups (2026-11-24, [mockup](mockups/intervals-variant-groups.html)).** Everything picked per set is a group: the first is the
variants (with a name, `Exercise.variantsName`, e.g. Range; it alone can change what a set records), the others are choice specs, shown
the same way. A group can be shown only for one option of another (`Spec.onlyWhen` = "Help=band": the band colors only with band;
`shownSpecs`). Edit exercise: Variants = the first group's name (tap to rename) and options, a card per other group (options, the groups
inside it), "+ Add a group"; numbers are "Measurements". Log: a row of chips per group near the button, the band colors only with band,
"Log set · first half · band"; values of hidden groups are not saved. The rows are tight (`GroupChips`): a small label, compact
chips, and a group whose options all have colors (bands) is dots only, no names (ring = picked, "×2", hold for one more). Records keep every combination apart (`recordKey`).
On the phone (by hand, backup `before-groups2.db`): One Arm Pull Up = Range full / first half / 90° hold (hold: weight + time) and Help
none / band / deload with Bands under band: deloaded → full + deload (193), resistance band → full + band (36, 28 with their colors).

**One Arm Pull Up without Help (2026-11-24, [mockup](mockups/intervals-range-bands.html), B).** Help (none / band / deload) repeated what
the set already says: a negative weight is the deload, a picked band dot is the band. Removed by hand (backup `before-nohelp.db`): Range
full / first half / 90° hold + Bands (always shown, nothing picked = no band); the 8 band sets with no color recorded got "band?" in
their note. The log panel: the variants with no label over them, then a slim line "BANDS ● ● ● ● ●" (a group of colors is one line,
its name at the left). In the logged sets the bands are small dots before the load, not "black+red" (`plainLoad`, `ColorDots`); the same on the Gym day
page, each set its own dots (`withoutColors`), black drawn dark grey (`bandDotColor`). 0 kg (body weight) is not written in set rows,
the last-set line under the variants, nor as "top" in the Journal line. The day page's rests are a fixed column, so L / R line up.
The last set under each variant is shown as that variant records it (90° hold: its time).

**Rest notification visible (2026-11-24).** It was on a low-importance channel ("rest"), which Android shows under Silent, with no
status bar icon and not on the lock screen. Now channel "rest_clock", normal importance, no sound or vibration of its own (the buzz at
the target is the app's vibration); the old channel is deleted. A rest restored at app start posts its notification again. The "Next"
line leaves out 0 kg. On Android 16+ it is a Live Update ([mockup](mockups/intervals-rest-notification.html), B):
`setRequestPromotedOngoing(true)` + POST_PROMOTED_NOTIFICATIONS, so the rest clock is a chip in the status bar (hidden by Android
while Intervals itself is on screen) and the card is pinned first; Android does not color promoted cards. No progress bar (it
would need updates every few seconds). Later (user asked): a teal `ProgressStyle` bar to the target, redrawn every 5 s by
`RestAlarm.refresh` from the app scope while the rest runs (stops at the target or if the process dies).

**Load hint can be swiped away (2026-11-24).** Swipe "↑ Try 32.5 kg" left or right: it stays away for that exercise, side, variant
and weight (SharedPreferences "hints"); a later, different suggestion still shows.

**After a set the values stay (2026-11-24).** The log used to load last time's set at the same place after every set (mockup
intervals-next-features.html, 1A), so Chin Up jumped 0 → 10 → 15 → 20 kg by itself following 30 Apr. Now only the first set of the
day starts from last time; after that the values stay as just logged. Per side: the next side takes its own last set today, else
last time's. Going heavier is the "↑ Try" hint's job.

## Syncing work from both PCs

Save a local Git checkpoint of uncommitted work before updating. Use the latest app as the base and carry forward the agreed behavior below wherever it is missing. Keep the latest screens, features, and database migrations. The previous working tree was saved on this PC at `8fadd24` on `safety/intervals-before-sync-20261123-123433`.

## Gym behavior to carry forward

- **Exercise tags and workout groups.** Tags describe exercises, such as Pull, Legs, or Fingers. Long-press exercises in the Gym library and use **Tag…** to assign them. A workout group is a reusable list of chosen exercises and set plans, such as Pull day. Create one via **Gym → + Add exercise → + New workout**. Journal colors follow the tags, with gray for untagged exercises.
- **Update one set.** Tap a logged set to load it into the existing inputs. **Update set** saves that entry while keeping its original day, order, rest, and note. Logging is unavailable while sets are selected.
- **Select sets.** A long press starts selection on the same screen with haptic feedback. One or more sets use the same pink toolbar with a count, **Note**, **Delete**, and an **X**. Tap a set to add or remove it from the selection. Each set is individually selectable, even when several share a row. With several selected, disable and dim the set inputs and logging button. **X** and Android Back exit selection.
- **Notes and deletion.** For one set, **Note** opens its existing note. For several sets, **Note** appends the same text to each and keeps their existing notes. Deletion asks for confirmation and applies to the selected sets.
- **Set-entry header.** The exercise name sits beside the rest timer when it is visible. Previous sets are available in **History**. The “Last time” line below the exercise name was removed.

## Last documented Pixel install

On 2026-11-23, the Pixel 8a received the build with the restored pink set editor and the simplified set-entry header via `adb install -r`. The installed APK matched the local build, and Intervals launched successfully.

- **Navigation.** Journal opens first. The bottom bar is Journal, Intervals, and Gym. The separate Today screen and its Quick Picks are gone. Gym opens on the exercise library. A day opened from a Journal card returns to that date when you go back.
- **Compact Journal.** The month shows sessions, days trained, and gym sets in one line. A gym day counts as one session alongside the interval sessions on that date. The workout-type pills and Time tile were removed from the calendar.
- **Calendar and logging.** The magnifying glass reveals exercise and tag search. An active filter still highlights matching dates when the field is closed. The selected day's **⋯** menu gives access to **Add exercise** or **Resume workout**. The selected date and filter survive tab changes.
- **Gym access.** Tap a library exercise to log it today. The Gym action opens the existing picker for adding exercises and starting saved workouts. Gym also offers **Resume workout** while today's workout is active.
- **Set order and editing.** The live log keeps sets in order. Consecutive sets with the same load, side, and variant share a row with individually editable rep counts. For example, 60 → 70 → 60 stays in three rows. Tap a set to edit it, or long-press and select sets for **Notes** and **Delete**.
- **Exercise tags.** Long-press exercises in Gym and tap **Tag…**. Tags can be created, added, or removed in bulk. Saved workout groups keep their exact exercise membership and plans.

The earlier workout and Stats changes are in the same history. Ring counters and simplified Gym Stats landed in `6cc234d`, and Most Trained's Sets, Days, and Reps landed in `8dac65c`. The Gym improvements, restored exercise library, and set multi-selection landed in `5aeffb2`, `0ee09eb`, and `4401f14`.

## Journal: month and the selected day (installed 2026-11-23)

The user tried the diary of option 2 in the [month and diary mockup](mockups/intervals-journal-month-diary.html) and found it awkward: the calendar vanished at the first scroll.

- **Calendar and day.** The month stays on top, always shown. Below it are only the selected day's workouts.
- **Swiping.** Swiping that part sideways goes to the day before or after. The calendar marks the day and follows into the next month.
- **Picking.** Tapping a day selects it. Swiping the month selects its last trained day.
- **Code.** The diary code (`Diary.kt`) is gone.

## Journal day: bars only (2026-11-23)

The table by load (A2) and its setting were removed; each exercise of the day shows only the bars (C2) from the
[Journal day ideas](mockups/intervals-journal-day-ideas.html).

- **Bars.** One bar per set, in the order done, always as high as the reps (or the time). Option **5 + N2 + P4** of the
  [bar marks mockup](mockups/intervals-journal-bar-marks.html): plain bars are purple; a per-side set has **L** / **R**
  written at the top inside the bar (no pink / green any more). With bands the bar is the band colors, striped like elastic
  (section 5, D: so the purple band differs from a plain bar), with a light outline (black drawn dark grey). The kg sit inside at the foot, only on the first bar of each weight (option D of the
  [bar weight mockup](mockups/intervals-journal-bar-weight.html)); nothing under the bars. Bars are 30 dp wide when the
  exercise has weights, else 24.
- **Notes and records.** A set with a note has its top-right corner folded; the note text shows in the exercise line
  when the bar is tapped (the italic list of set notes is gone, the day note stays). A record set (`recordSetsOn`, the
  same rule as the "N PR" count) has a small gold cup over its reps; the reps are no longer yellow.
- **Tap a bar:** it is outlined and the exercise's line reads "#4 of 11 · after 30 × 5 · before 37.5 × 5", or "#3 of 8 · black · 3 · “last rep half”" when the set has a note; " · PR" on a record.
- **Code.** `dayBars` in `GymRows.kt`, with tests.

## Gym day screen: timeline (2026-11-23, not installed yet)

The screen a Journal gym card opens (`DayPage` in `GymScreen.kt`) is option **E** of the
[gym day ideas](mockups/intervals-gym-day-ideas.html): the old table per exercise (bands not shown, L / R on eight rows)
is replaced by one timeline card. Every set in the order done under its exercise's name, with a pink summary on the right
("4 rounds · black", "9 × 1 · 4 bands", "6 sets · 35 reps"); an L set and the R right after share one row; the band dot,
load, reps in bold, the note; the rest before the set (or round) on the right, yellow over 5 min; a strip in the tag
color on the left. Tapping opens the exercise to log or edit. The "last time" line is gone.

## Log tab: the logged sets (2026-11-23, not installed yet)

From the [logged sets ideas](mockups/intervals-log-sets-ideas.html) (`SetBlocks` in `GymLogScreen.kt`):
- **W1**, exercises without sides: a block per load in the order done ("15 kg", "90 kg normal"); the load has one width
  for all blocks so the squares line up; the rest before each set is under its square, yellow over 5 min.
- **U**, per-side exercises: one row per round, L left and R right with a line between (the L and R right after each
  other, in either order); a set without its other side leaves that half empty; "L 5 · R 7" above; the rest before the
  round on the right. The day screen pairs the same way (`pairSides`) and its summary says "L 5 · R 7" when uneven.
- A note is a folded corner on the square (pink border now only means selected). Tap to edit, hold to select, as before.

## Exercise History (2026-11-23, not installed yet)

Option **H2** of the [history ideas](mockups/intervals-history-ideas.html) (`ExerciseHistory` in `GymExerciseViews.kt`):
each day the Journal's bars (`DayBars`, now `internal`, with the cup on records), the rest before each set under its
bar (yellow over 5 min), a pink summary ("11 sets · 53 reps · top 40 × 3", "4 rounds", "L 5 · R 7") with ▲ / ▼ for
the volume against the session before, and "⏱ 1 h 01 on this exercise · workout 2 h 38": the exercise time is the sum
of the rests after its first set, the workout the sum of all rests of the day (the rest clock counts from the previous
set, so this is first to last set). Days logged before the rest clock show no times.

## Add exercise and New workout (2026-11-23, not installed yet)

- **Add exercise** (`GymPickScreen`), option A of the [add exercise ideas](mockups/intervals-add-exercise-ideas.html)
  with B's workouts on top: search and + New; Workouts (+ New); then chips Recent / All / each tag (in its Journal
  color) filtering the exercises, each with its tags written under its name (option 3 of the [tag dots mockup](mockups/intervals-tag-dots.html)). Tap logs; hold selects several (numbered in the
  order picked) and **Workout** opens a new workout with them. No tag making or editing here: that stays in the Gym list.
- **New workout** (`EditGroupScreen`), option B of the [new workout ideas](mockups/intervals-new-workout-ideas.html):
  no Tag / Workout switch, no set plans (decided while training), small color dots, drag ⋮⋮ to reorder, and the
  exercises listed below to tick (with the tag chips). Old `planSets` / `planNote` stay in the data, unused here.

## Restored after the sync (2026-11-23)

From the pre-sync checkpoint `safety/intervals-before-sync-20261123-123433`: Settings has **Back up and restore** and
**Import from another app** again (with dividers and accessible rows), and Edit exercise has **Merge another exercise
into this** (similar names first; optionally as one of its variants; groups keep the target). The checkpoint's exercise
notes and tag-chips-on-exercise were not brought back: tags are groups now.

## Next features batch (2026-11-23, built, not installed yet)

From the [next features mockup](mockups/intervals-next-features.html):
- **1A, last time in the inputs.** `startValues` now starts from last time's set at the same place (3 sets today → last
  time's 4th; per side, the Nth L from last time's Nth L), else today's last set. After each logged set the inputs move
  on to the next one. No "from last time" line.
- **2A + 2B, sets per tag.** `SetsPerTagCard` in Stats (Gym and Both): "This week" bars with a tick at the 3-week average
  and the difference; "4 weeks" small charts per tag. An exercise counts for its first tag; untagged grouped.
- **3A, presets.** + New in Intervals opens `NewWorkoutScreen`: Start empty, then sections (now only **Fingers**:
  Repeaters 7/3, Max hangs 10 s, Density hangs; `core/Presets.kt`), each with its shape. A preset opens in the editor
  to save as yours. Their Hang series log Edge (mm) and Load (kg).
- **3B, set before the start.** A workout with log fields opens `SetupScreen` first (− / +, prefilled from last time);
  the values show on the disc during that series ("20 mm · 5 kg") and prefill the Finished screen. Grip chips from the
  mockup are not done: log fields are numbers only.
- **3C, in the Journal.** A session card adds "▲ Load +2.5 kg · same Edge (Tue 10 Nov)" against the previous session of
  the same workout (`valuesChange`).

## Rest timer (installed 2026-11-23, to try)

Built from option **3** in the [rest timer mockup](mockups/intervals-rest-timer.html).

- **Pill in the header.** On the set screen the rest is a pill beside the exercise name: "⏱ 1:12 / 3:00", then a filled "Ready 3:14". Tapping it stops the clock. The rest is still saved on the next set, because the clock only hides. The group run screen keeps the old wide clock.
- **Notification.** `RestAlarm.kt` shows a notification with a chronometer, **Stop**, and **Open**, also on the lock screen. A forgotten rest goes away by itself 15 minutes after the target.
- **Buzz.** The buzz at the target comes from an exact alarm (`USE_EXACT_ALARM`), so it works with the app closed. The notification then turns to "Ready".
- **Check on the phone:** the buzz with the app swiped away, Stop from the notification also hiding the pill, and that there is only one buzz.

## Set screen: A + B1 (installed 2026-11-23, being compared)

From the [set screen ideas](mockups/intervals-set-screen-ideas.html), the user chose **A** and **B1**.

- **A, spec chips.** The specifications are one row of chips ("20 mm ✎", "half crimp ✎"). Tapping one opens a dialog, and a single choice closes it.
- **B1, three buttons.** For a per-side exercise, the L / R / Both switch is replaced by **Log L · L + R · Log R**, with the next side lit in its color. Editing a set still has the L / R switch.
- **Trial setting.** Settings › Workout › "Gym log buttons at the bottom" puts the buttons fixed at the bottom (on, the default) or after the numbers, as before (off). Remove the setting once the user picks one.
- **Variants (A5a from the [variant mockup](mockups/intervals-variant-switch.html), installed 2026-11-24).**
  - The switch at the top is gone. The variants are one row just above the log buttons, each showing its last set ("normal · 90 × 5").
  - With more than three variants the row scrolls, and the fourth is cut off to show it goes on.
  - Tapping a variant switches to it and brings back that variant's last numbers (`lastOfVariant`, on the same side). Editing a set changes only its variant.
  - Not yet seen on the phone.
- **L / R sets (A + B from the [side set mockup](mockups/intervals-side-set-actions.html), installed 2026-11-24).**
  - A per-side exercise shows its sets as rounds: one row per L + R pair done one after the other (`sideRounds`), the note under the reps.
  - The load appears once above its rounds, or in each half when the two sides differ. ⋯ selects the whole round.
  - With sets selected, ✕ · Note · Delete replace the log buttons (Update set below them for a single set). The pink bar on top is gone.
  - Delete has no confirmation: «Deleted … · Undo» for 6 s, which puts the rows back as they were (`restoreSets`).
  - Replaced on 2026-11-24 by this PC's U rounds (see the merge section at the top); the bottom actions and Undo stay.
- **Pinch: size became variants (phone data, 2026-11-24).** Pinch (id 38) had a spec «size» (small · medium · large). It now has the variants medium · small · large and no spec. Today's set became variant medium. The older sets (until 14 Oct) have no variant written, so they count as medium: they were on a different pinch, and the user chose to keep them together. They are the only Pinch sets without a variant, so they can be split later. Backup on the phone: `databases/before-pinch.db`. Finger Pinch keeps «width» as a spec.
- **Later.** D (START for timed sets).

## Stats (2026-11-23, built, not installed yet)

Built from the [Stats screen mockup](mockups/intervals-stats-screen.html), replacing the pending option B work:
- **Way in.** The Journal's Calendar / Stats switch is gone. The month line ("15 sessions · 13 days trained · 210 gym
  sets  Stats ›") is a button that opens Stats on that month (`Overlay.Stats`); Back returns to the same day.
- **Stats screen** (`StatsScreen.kt`): ← back, Intervals / Gym / Both, Week / Month / Year with ‹ › to go back in time
  (not past today). Everything is tappable: the tiles open lists (`Overlay.StatsList`, `StatsListScreen.kt`: interval
  sessions, gym days, exercises by sets, records); the minutes bars open the Journal on that day or week (a year's month
  bar opens that month in Stats); gym days per week bars open the Journal on that week's first gym day; records open the
  exercise's Records tab (`Overlay.GymLog.tab`), most trained its History, most done intervals their sessions.
- **Both** shows "Training per day" (option 2 of the [Both chart mockup](mockups/intervals-stats-both-chart.html)):
  per day a blue block for a gym day and a pink one for interval sessions; a year shows per month the gym days and the
  interval days. On the right: "13 days · 3.2 / week" (days trained in the period, up to today). Minutes stay under Intervals.
- **Records** are now those set in the period, computed against the whole history (before: last 30 days against 12 weeks).

The approved Journal design is option **A** in the [Journal concepts](mockups/intervals-journal-concepts.html).

## Verification

- The core and Android unit suites passed all **91 tests** after restoring the Gym set editor. The debug APK built successfully after the header change.
- An earlier Pixel check confirmed Journal as home, the new bottom tabs, and the Gym library. The filter and day-menu walkthrough ended when the phone switched to Gym. The restored set editor still needs a walkthrough on the Pixel.
- The updated Stats mockup was checked in Edge. Both entries open Stats, option B opens the month being viewed, and Back restores the Journal date and filter. Both layouts fit a 320 px preview.
- See the [build and install commands](../README.md#command-line). Keep machine-generated `gradle/gradle-daemon-jvm.properties` local.

## Earlier work from 2026-11-18

The notes below describe that day's build and design questions. The current decisions are above.

### Player and app

- **Opening**: no "Get ready" 3-2-1 any more (the setting was removed; the warm-up is the get-ready). The player opens with
  a 1 s animation (disc grows with a small overshoot, rings come out from behind it); the workout clock starts after it
  (`PlayerController.openingMs`, set to 1000 in `AppViewModel`; the UI tween in `Disc` must match).
- **Last-3-seconds digit** (`CountdownDigit` in PlayerScreen.kt): starts 4x and faint, shrinks into the center (no drop),
  solid at the end. Two-tone: drawn twice, the copy clipped to the disc uses the auto ink (dark/white), the outer copy
  the disc color. Mockups: https://claude.ai/artifact/L3yeuVckD9uTiJQVpXf1sa (option D chosen).
- **Pause**: rings spiral into the disc (turn 540°, shrink behind it, fade, 650 ms); reverse on resume.
- **Smooth rings**: the ticker wakes once per second while visible; the rings/dot read the clock every frame in the
  draw phase only (`frameMs` + `withFrameMillis` in `Disc`), so the screen does not recompose per frame.
- **App icon**: week ring Mon-Sun around a purple disc with a check; adaptive + themed (`res/drawable/ic_launcher_*`,
  `res/mipmap-anydpi`). Options page: https://claude.ai/artifact/1z82bwzzMouzMLoaQFaDhY (H with E's purple).
- **Today**: the big Intervals ▶ / Gym ▶ buttons were removed (they only switched tab).
- Gym work from earlier sessions (finish screen, group run, DB v4) is in the same commit.

### Design questions at that point

- Text color of the name/time on the disc: https://claude.ai/artifact/TiN6U7ZHbLVJa5iVWKSmpM (first section, A-F;
  suggested D: white text, light discs darkened ~30%).
- Small numbers on the rings: same page, second section, options 1-6 (suggested 6: "3/6" chip by the dot, "2/4" on
  the rounds ring).
- Today looks empty when nothing was done recently (Quick start lists only recent workouts); maybe a fallback.

### Build and install on Linux

```bash
cd ~/intervals
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:assembleDebug :app:testDebugUnitTest :core:test
~/Android/Sdk/platform-tools/adb shell dumpsys activity services app.intervals | grep -c "ServiceRecord.*PlayerService"  # 1 = a workout is running: ask before installing
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Testing on the phone: start a workout from the Intervals tab, burst `adb exec-out screencap -p` to see animations;
afterwards stop it (X, Stop) and Discard (twice) so nothing lands in the journal.
