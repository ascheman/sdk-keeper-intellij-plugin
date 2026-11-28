# SDK Keeper

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/36378?label=Marketplace)](https://plugins.jetbrains.com/plugin/36378-sdk-keeper)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/36378)](https://plugins.jetbrains.com/plugin/36378-sdk-keeper)
[![Build](https://github.com/ascheman/sdk-keeper-intellij-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/ascheman/sdk-keeper-intellij-plugin/actions/workflows/build.yml)
[![SDKMAN compatibility](https://github.com/ascheman/sdk-keeper-intellij-plugin/actions/workflows/sdkman-contract.yml/badge.svg)](https://github.com/ascheman/sdk-keeper-intellij-plugin/actions/workflows/sdkman-contract.yml)
[![License](https://img.shields.io/github/license/ascheman/sdk-keeper-intellij-plugin)](LICENSE)

An IntelliJ IDEA plugin that keeps the IDE's Java SDK list lean and current, based on the JDKs installed with [SDKMAN!](https://sdkman.io).

> [!NOTE]
> SDK Keeper is an independent project.
> It uses SDKMAN! but is not part of it, not affiliated with it and not endorsed by the SDKMAN! maintainers.
> Please report problems with the plugin [here](https://github.com/ascheman/sdk-keeper-intellij-plugin/issues), not to SDKMAN!.

IntelliJ never cleans up its SDK list: every `sdk install java ...`, every project that asks for a JDK name it doesn't know, every Gradle/JBang toolchain adds another entry.
After a few years the list is long, full of `temurin-21 (7)`-style duplicates and dead paths, and new projects still default to whatever happened to be configured years ago.

## Stable JDK paths: `~/.jdks-lts/<major>`

SDKMAN has `~/.sdkman/candidates/java/current`, but no stable path per *major version*: every patch release lives in its own directory (`21.0.10-tem`, `21.0.13.0+9-tem`, ...).
Anything that needs a fixed JDK path (Maven `toolchains.xml`, scripts, other IDEs, `JAVA_HOME` of services) breaks or silently stays on an old patch after each update.

The plugin therefore maintains one symlink per managed version:

```text
~/.jdks-lts/8  -> ~/.sdkman/candidates/java/8.0.512.0+9-zulu
~/.jdks-lts/17 -> ~/.sdkman/candidates/java/17.0.21.0+9-tem
~/.jdks-lts/21 -> ~/.sdkman/candidates/java/21.0.13.0+9-tem
~/.jdks-lts/25 -> ~/.sdkman/candidates/java/25.0.5.0+8-tem
```

Point your tools to `~/.jdks-lts/21` instead of `~/.sdkman/candidates/java/21.0.10-tem`, and they follow every update without further changes.
The links are only replaced, never real directories, and the directory is configurable (or the feature can be switched off).

Optionally, the plugin also keeps Maven's `~/.m2/toolchains.xml` current (off by default): the `jdkHome` of each JDK toolchain with a plain version (`1.8`, `11`, `17`, ...) and a matching vendor is pointed to the symlink (or, if you prefer, to the versioned SDKMAN path).
Version ranges, other vendors, other toolchain types, comments and formatting are left alone; a backup of the previous file is kept as `toolchains.xml.bak`.
Adding toolchains for missing versions is a separate opt-in.

## Features

- **One SDK per Java version.**
  For each managed major (default: all LTS versions, as published by the [foojay Disco API](https://api.foojay.io)) there is exactly one Java SDK, named by its major (`8`, `11`, `17`, `21`, `25`), pointing to the newest installed SDKMAN patch release.
- **Cleanup that respects your choices (experimental, off by default).**
  Settings that name a removed SDK break: closed projects, the Gradle JVM, Maven's runner and run configurations are not updated.
  If you opt in, other Java SDKs under SDKMAN are removed, also if their home no longer exists; JDKs elsewhere (JBang, Gradle, manual installations, unplugged disks) stay unless you opt in to that as well.
  Open projects and modules using a removed SDK only move within the same Java version.
  Without an SDK of that version the project is left alone: the SDK stays, and if it no longer exists a warning names the project and offers to switch.
  If another component adds a Java SDK later, the plugin cleans up again.
  The notification offers *Restore and keep* for anything you want to retain, names on the "Never remove" list and existing early-access JDKs always stay, and a managed SDK you delete yourself is not re-created.
- **JDKs outside SDKMAN (experimental, off by default).**
  Homebrew's `openjdk*` formulae, `/Library/Java/JavaVirtualMachines`, `/usr/lib/jvm` and JDKs downloaded by the IDE can be chosen as well.
  Version and vendor come from the JDK's `release` file (`brew` for Homebrew builds), so add their vendor to the vendor preference to use them.
  They are never removed.
- **Defaults for new projects.**
  "Settings for New Projects" use the newest managed version and its language level.
- **Update notifications.**
  When SDKMAN offers a newer release of a managed line (same major, vendor and variant), a notification offers to install it with one click (`sdk install java ...`, without changing your SDKMAN default).
  After installation the SDK entry is repointed.
- **`.sdkmanrc` check.**
  When a project with an `.sdkmanrc` is opened and its `java=` entry is outdated, a notification offers to update it to the latest release of the same line or to the latest LTS line (installing it via SDKMAN if necessary).
  An early-access pin is offered newer early-access builds, and the release once there is one; other pins are never offered early access.
  For completion while editing `.sdkmanrc`, see the [SDKMAN plugin by SivaLabs](https://plugins.jetbrains.com/plugin/30654-sdkman), a separate project; both plugins work side by side.

Syncs run on startup, hourly, after settings changes and via *Tools | Sync JDKs with SDKMAN Now*.

## Settings

*Settings | Tools | SDK Keeper*

| Setting | Default |
|---|---|
| SDKMAN directory | `$SDKMAN_DIR` or `~/.sdkman` |
| Managed versions | empty = all LTS versions; or e.g. `17, 21, 25` |
| Vendor preference | `tem, zulu, amzn` (SDKMAN vendor suffixes) |
| Keep one Java SDK per managed version | on |
| Also choose from JDKs outside SDKMAN (experimental) | off |
| Remove other Java SDKs under SDKMAN, including dead ones (experimental) | off |
| Also remove Java SDKs outside SDKMAN | off |
| Never remove | empty; comma-separated SDK names |
| Do not re-create | filled automatically when you delete a managed SDK |
| Default for new projects | empty = newest managed version |
| Maintain one symlink per managed version | on, in `~/.jdks-lts` |
| Update Maven toolchains.xml | off; then: `~/.m2/toolchains.xml`, point to symlinks: on, add missing: off |
| Notify about newer releases | on |
| Check `.sdkmanrc` on project open | on |
| Offer only LTS versions as "latest line" | on |

## Installation

In the IDE: *Settings | Plugins | Marketplace*, search for **SDK Keeper**, or install it from its [Marketplace page](https://plugins.jetbrains.com/plugin/36378-sdk-keeper).
Your own builds (see [Building](#building)) go in via *Install Plugin from Disk…*.

## Requirements

- IntelliJ IDEA 2026.3 or later (Community or Ultimate)
- SDKMAN! on macOS or Linux (installation uses `bash` and `sdkman-init.sh`)

## Troubleshooting

If the IDE misbehaves after installing or updating the plugin:

- Disable it under *Settings | Plugins*.
  If the IDE does not start, add the plugin ID `net.aschemann.sdk-keeper` as a line to `disabled_plugins.txt` in the IDE's configuration directory (macOS: `~/Library/Application Support/JetBrains/<product><version>/`, Linux: `~/.config/JetBrains/<product><version>/`).
- Or start the IDE once without any third-party plugins: `idea disableNonBundledPlugins`.
- Changes outside the IDE are easy to undo: `~/.jdks-lts` only contains symlinks, and the previous `~/.m2/toolchains.xml` is kept as `toolchains.xml.bak`.
  The plugin never deletes JDKs.

Errors during a sync or on project startup are written to `idea.log` (*Help | Show Log*), they never block the IDE.
If the IDE freezes or shows an error caused by SDK Keeper, please send the report from the IDE's error dialog (*Report to third-party plugin*); it reaches the plugin's author via the Marketplace.
CI verifies the plugin against current IDE releases and, weekly, runs its tests against the newest EAP.

## Building

```shell
# against a local IDE installation (fast, no download)
./gradlew buildPlugin -PplatformLocalPath="$HOME/Applications/IntelliJ IDEA.app"

# against a downloaded IDE (platformType/platformVersion in gradle.properties)
./gradlew buildPlugin
```

The plugin ZIP ends up in `build/distributions/`; install it via *Settings | Plugins | ⚙ | Install Plugin from Disk…*.
`./gradlew runIde` starts a sandbox IDE with the plugin.

The `core` package has no IntelliJ dependencies and is covered by plain JUnit tests.
The synchronization of the IDE's SDK table is covered by platform tests, which run a headless IDE against a fake SDKMAN directory.
Both run with `./gradlew test`; CI also runs them weekly against the newest IntelliJ EAP.

[docs/diagnosing-freezes.md](docs/diagnosing-freezes.md) describes how to investigate freeze reports.
`./scripts/marketplace-media.sh` takes the Marketplace screenshots in a Linux container; see [docs/marketplace-media.md](docs/marketplace-media.md).
`./scripts/publish.sh` uploads the version in `gradle.properties` to the Marketplace and tags it; the token comes from the macOS Keychain (see the script).

`SDKMAN_CONTRACT=true ./gradlew contractTest` checks the real SDKMAN CLI and the SDKMAN and foojay APIs the plugin relies on, as a compatibility check on our side.
It installs the newest Temurin LTS into `$SDKMAN_DIR` if it is missing.
CI runs it weekly on Linux and macOS, so changes on SDKMAN's side show up as a failed run; [CONTRACT.md](CONTRACT.md) records what changed and when.

## Roadmap

- Remove superseded patch releases from SDKMAN after an update (opt-in)
- Maven runner JRE / Gradle JVM defaults
- Gradle toolchain configuration (`org.gradle.java.installations.paths`)
- Other SDKMAN candidates (Maven, Gradle, Groovy, ...): IDE settings and `.sdkmanrc` checks
- Safer cleanup: update Gradle JVM, Maven settings and run configurations, or keep SDKs they use
- JDKs outside SDKMAN: per-source selection, Windows locations
- Follow the native SDKMAN CLI: a stable, machine-readable interface instead of the directory layout
- LTS versions from the SDKMAN State API instead of foojay, once it publishes them
- Recover from interrupted SDKMAN downloads (a leftover partial download blocks every retry)
- Windows support (SDKMAN via WSL/Git Bash)

## License

Copyright 2026 Gerd Aschemann.
Licensed under the [Apache License 2.0](LICENSE).
SDKMAN! is a separate project by its own maintainers; SDK Keeper uses it but is not affiliated with it.
