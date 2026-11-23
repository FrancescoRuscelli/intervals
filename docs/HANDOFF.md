# Intervals handoff

Updated 2026-11-23. Read [CLAUDE.md](../CLAUDE.md) for repo conventions and [DESIGN.md](DESIGN.md) for design history. The current navigation decision is recorded here.

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

## Journal day: bars and the table by load (installed 2026-11-23)

The user could not choose between ideas C2 and A2 in the [Journal day ideas](mockups/intervals-journal-day-ideas.html), so both are built.

- **Setting.** Settings > Workout > "Gym sets in the Journal" offers Bars, Table, or Both. The default is Both.
- **Bars (C2).** There is one bar per set, in the order done. The height is always the reps (or the time).
  - Band colors fill the bar.
  - The side is a cap on top: L pink, R green.
  - The kg are written under the bar. The reps are above it, in yellow when the set has a note.
- **Table (A2).** The sets are grouped by load, lightest first, with the band dots as the label. L and R boxes are tinted pink and green.
- **Set order (F + I from the [set number mockup](mockups/intervals-set-number.html)).** The user chose these two together for now and will decide later.
  - The boxes are clean. A thin yellow strip under a box marks a note.
  - **Hold a box:** every box shows its "#n" tab.
  - **Tap a box or a bar:** that set is outlined in both views.
  - While a box is held or a set is picked, the exercise's line reads "#4 of 11 · after 30 × 5 · before 37.5 × 5".
- **Code.** The logic is `loadRows` and `dayBars` in `GymRows.kt`, with tests.

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
- **Not changed yet.** The variant switch stays as it is, for the one-tap switch between deep and normal. D (START for timed sets) is for later.

## Journal and Stats decision

The installed app still uses the **Calendar / Stats** switch inside Journal. The user prefers option **B** in the [Stats mockups](mockups/intervals-journal-stats.html). The revised mockup gives the monthly summary a visible **Stats ›** action so its destination is clear.

The proposed Android change opens a separate Stats screen starting on the Journal month the user selected. **Back** returns to the same date and filter. The browser mockup implements and tests that flow with sample data. The native Stats route and historical-month scope are pending.

The approved Journal design is option **A** in the [Journal concepts](mockups/intervals-journal-concepts.html). The [set-order mockup](mockups/intervals-chronological-log.html) shows the live set grouping. For earlier designs, see the [four-tab preview](mockups/intervals-navigation.html), [navigation comparison](mockups/intervals-navigation-options.html), and [Journal layout comparison](mockups/intervals-journal-options.html).

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
