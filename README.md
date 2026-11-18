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

./gradlew :core:test                 # unit tests (model, timeline, importer, backup format)
./gradlew :app:testDebugUnitTest     # database tests, on the PC (Robolectric)
./gradlew :core:test -Pintervals.csv=/path/to/export.csv   # also check a real Hybrid Interval Timer export
./gradlew :app:assembleDebug         # APK in app/build/outputs/apk/debug/
./gradlew :app:installDebug          # install on the connected phone
```

`local.properties` (not in git) must contain `sdk.dir=/home/<you>/Android/Sdk` when building from the command line.

### Windows

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-toolsdb.exe" install -r appuild\outputspk\debugpp-debug.apk
```

`local.properties`: `sdk.dir=C\:\Users\<you>\AppData\Local\Android\Sdk`. The SDK can be installed without the
Android Studio wizard: unzip the command-line tools to `Sdk\cmdline-tools\latest`, then
`android sdk install platforms/android-37.0 build-tools/37.0.0 platform-tools`.
Don't commit `gradle/gradle-daemon-jvm.properties` if Gradle creates it.

### Updating the phone from any PC

Debug builds are signed with the shared key `app/debug.keystore` (in git), so every PC can `install -r` over the app
without uninstalling (no data lost). A phone that still has a build signed with an old personal key needs one uninstall first.

## Layout

- `core/`: plain Kotlin: data model, timeline (exact play order and total), Hybrid Interval Timer CSV importer.
- `app/`: Android app (Jetpack Compose); `data/` = Room database + repository; `schemas/` = database schema history.
- `docs/`: design decisions and a copy of the mockups.
