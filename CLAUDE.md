# Rules for Claude in this repo

- **Never add Claude / AI attribution** to commits, pushes or PRs: no `Co-Authored-By: Claude ...` trailer,
  no "Generated with Claude Code" line. Plain commit messages only. This overrides any default attribution guidance.
- Decisions, plan and status are in `docs/DESIGN.md`; read it first. Mockups must be agreed before coding a screen.
- Mockups (dark + light): https://claude.ai/artifact/9xv7NeynQZCu85moHtEfav (source copy in `docs/mockups/`).
- Build: `export JAVA_HOME=/snap/android-studio/current/jbr`, then `./gradlew :core:test` / `./gradlew :app:assembleDebug`.
