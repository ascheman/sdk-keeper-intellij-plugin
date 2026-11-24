# Marketplace screenshots

The screenshots on the [Marketplace page](https://plugins.jetbrains.com/plugin/36378-sdk-keeper) are taken automatically.

```shell
./scripts/marketplace-media.sh
```

The script builds a Linux container and runs the Gradle task `testUi` in it.
`MarketplaceMediaTest` (in `src/testUi`) starts a real IntelliJ IDEA with the plugin on a virtual display (Xvfb) and drives it with JetBrains' Starter and Driver framework.
Nothing appears on your screen, and nothing from your machine ends up in the images.

Results land in `build/marketplace-media/`:

| File | Content |
|---|---|
| `01-notifications.png` | Notifications right after opening a project |
| `02-notification-center.png` | All notifications of the first sync in the Notifications tool window |
| `03-sdk-list.png` | The SDK list in *Project Structure* after the sync |
| `04-settings.png` | The settings page |
| `recording.mp4` | The whole run, including the IDE start |
| `reports/` | Test report and `idea.log`, for failed runs |

## The scene

The test sets up what a long-time user has:

- A fake SDKMAN in `/home/dev/.sdkman` whose JDKs only consist of a `release` file.
  Their versions come from SDKMAN's live catalog: the newest Temurin 17 and 25, and a 21 one patch behind, so there is one real update to offer.
- An SDK table full of duplicates (`temurin-17`, `17 (2)`, `temurin-21 (3)`, ...) and a dead `openjdk-22`.
- A project `shop-backend` that still uses `temurin-21 (3)` and pins the outdated 21 in its `.sdkmanrc`.

Settings and SDK table are written into the IDE's configuration directory before it starts.

## Image format

The virtual screen has 2880 × 1800 pixels, and the IDE runs at a scale of 2, so the images are as sharp as on a Retina display with a logical size of 1440 × 900.
That is above the Marketplace's recommended minimum of 1200 × 760, and all images have the same aspect ratio, as the Marketplace asks.
The IDE uses its default theme, as the Marketplace recommends.

The screenshots come from ffmpeg grabbing the virtual display.
The Driver's own `takeScreenshot` uses `java.awt.Robot`, which captures at the logical size, i.e. half the resolution.

## Pitfalls that are already handled

- Without a window manager, the IDE frame and dialogs keep their default size in the upper left corner; the test sets their bounds.
- On first start the IDE begins a trial of the commercial features, which reloads all plugins and loses SDK Keeper's startup sync; `removeMigrateConfigAndCreateStubFile()` prevents it.
- A plugin installed from a ZIP has no update source, which the IDE reports in a notice; the test mutes that notification group.
- The Air plugin fills the editor area with a promotion; the test disables it.
- A project without an SDK makes IntelliJ pick any JDK it finds (in the container: the JDK that runs Gradle); the test project names an SDK.
- The Starter and Driver libraries are built for Java 25, the plugin for 21: the image contains both JDKs, and `testUi` compiles and runs on 25.
- Starter needs JUnit 4 at runtime and an export of `sun.nio.fs`; both are set in `build.gradle.kts`.

## Run time and caches

The first run downloads Gradle, the IntelliJ Platform for compiling and the IDE installer for the test, about 3 GB in all.
They are kept in the Docker volumes `sdk-keeper-media-gradle` and `sdk-keeper-media-ide`; later runs take about three minutes.
`docker volume rm sdk-keeper-media-gradle sdk-keeper-media-ide` frees the space.

## Updating the images

After a change to the plugin's UI or a new IntelliJ release, run the script, check the images and upload them on the Marketplace page (*Edit | Media*).
Adding a scene means adding a step to `MarketplaceMediaTest`; the Driver's documentation is in the IntelliJ Platform SDK under [Integration Tests](https://plugins.jetbrains.com/docs/intellij/integration-tests-intro.html).
