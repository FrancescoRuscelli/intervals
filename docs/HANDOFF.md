# Handoff for Claude

Updated 2026-11-18 (evening). Read [CLAUDE.md](../CLAUDE.md) and [DESIGN.md](DESIGN.md) first; DESIGN.md is the source of truth.
Everything below is committed and installed on the Pixel 8a.

## Done today (player and app)

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

## Open decisions (mockups waiting for the user)

- Text color of the name/time on the disc: https://claude.ai/artifact/TiN6U7ZHbLVJa5iVWKSmpM (first section, A-F;
  suggested D: white text, light discs darkened ~30%).
- Small numbers on the rings: same page, second section, options 1-6 (suggested 6: "3/6" chip by the dot, "2/4" on
  the rounds ring).
- Today looks empty when nothing was done recently (Quick start lists only recent workouts); maybe a fallback.

## Build and install

```bash
cd ~/intervals
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew :app:assembleDebug :app:testDebugUnitTest :core:test
~/Android/Sdk/platform-tools/adb shell dumpsys activity services app.intervals | grep -c "ServiceRecord.*PlayerService"  # 1 = a workout is running: ask before installing
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Testing on the phone: start a workout from the Intervals tab, burst `adb exec-out screencap -p` to see animations;
afterwards stop it (X, Stop) and Discard (twice) so nothing lands in the journal.
