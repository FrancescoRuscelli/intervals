# Intervals

Android interval timer for hangboard and HIIT workouts: build workouts (warm-up, rounds, rest between rounds,
repeat groups, per-side series), run them with a big concentric disc player, keep a journal and stats,
and import workouts from Hybrid Interval Timer.

Design decisions, plan and status: [docs/DESIGN.md](docs/DESIGN.md).

## Open and run

1. Install Android Studio (Linux: `sudo snap install android-studio --classic`), run its setup wizard (Standard).
2. *File → Open* this folder. Android Studio creates `local.properties` with your SDK path.
3. Run the `app` configuration on a phone with USB debugging, or on an emulator.

## Command line

```bash
# Java: Android Studio's bundled JDK works
export JAVA_HOME=/snap/android-studio/current/jbr      # path for the snap install

./gradlew :core:test                 # unit tests (model, timeline, importer)
./gradlew :core:test -Pintervals.csv=/path/to/export.csv   # also check a real Hybrid Interval Timer export
./gradlew :app:assembleDebug         # APK in app/build/outputs/apk/debug/
./gradlew :app:installDebug          # install on the connected phone
```

`local.properties` (not in git) must contain `sdk.dir=/home/<you>/Android/Sdk` when building from the command line.

## Layout

- `core/`: plain Kotlin: data model, timeline (exact play order and total), Hybrid Interval Timer CSV importer.
- `app/`: Android app (Jetpack Compose).
- `docs/`: design decisions and a copy of the mockups.
