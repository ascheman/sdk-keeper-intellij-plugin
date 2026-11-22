# Changelog

## [0.1.0] - 2026-11-22

### Added

- One Java SDK per managed major (default: LTS versions from foojay), pointing to the newest
  installed SDKMAN release; removal of all other Java SDKs with remapping of open projects
- Default SDK and language level for new projects
- Notifications about newer releases of the managed lines, with one-click installation via SDKMAN
- `.sdkmanrc` check on project open with updates to the latest release of the line or the latest LTS line
- Cleanup of other SDKs is experimental and off by default, since settings that name a removed SDK
  break; it keeps JDKs outside SDKMAN unless asked to; "Never remove" list, *Restore and keep*
  action, and managed SDKs deleted by the user are not re-created
- Experimental: choose from JDKs outside SDKMAN (Homebrew, system and IDE-downloaded JDKs), which
  are never removed
- Projects are never moved to another Java version without asking; a dead SDK that is still
  selected stays, with a warning and a *Switch to* action
- Early access: pinned builds in `.sdkmanrc` get newer builds and the release; existing early-access
  SDKs are not removed; builds rank below the release of the same version
- JDKs are recognized by their `release` file: aborted installations are ignored, and early-access
  JDKs are kept wherever they live
- JDKs built for another architecture (Intel JDKs on Apple silicon, which need Rosetta) are never
  chosen
- Installations are confirmed via `sdk home`
- Stable per-major symlinks (`~/.jdks-lts/<major>`) for tools outside the IDE
- Optional updates of Maven's `toolchains.xml` (only `jdkHome` of matching JDK toolchains)
- Settings page and *Tools | Sync JDKs with SDKMAN Now*
- Failures on project startup are only logged and never get in the way of opening a project
