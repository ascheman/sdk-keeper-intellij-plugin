# Diagnosing UI freezes

What to do when a user reports that SDK Keeper froze the IDE, or a freeze report shows up on the Marketplace.
Based on Jakub Chrzanowski's video [Investigate UI Freezes in Your JetBrains Plugin](https://www.youtube.com/watch?v=5Pdkj4lbTuc), recommended by JetBrains.

## Where the reports come from

The plugin registers the built-in Marketplace reporter in `plugin.xml`:

```xml
<errorHandler implementation="com.intellij.diagnostic.JetBrainsMarketplaceErrorReportSubmitter"/>
```

Users can then send exceptions and freezes from the IDE's error dialog (*Report to third-party plugin*), or automatically if they enabled automatic error reporting.
Reports appear on the plugin's Marketplace management page in the *Exceptions* and *Freezes* tabs, but only when the IDE attributes the freeze to SDK Keeper.

A freeze report holds an excerpt of the involved threads and full thread dumps (`dump-N.txt`, one every five seconds during the freeze).
It may also hold a CPU snapshot (`snapshot.jfr`), a stack analysis (`report.txt`) and performance metrics (`open-telemetry-metrics.csv`).

## How to investigate

1. Check the plugin version the report came from.
   If it is missing, do not assume it is the latest release.
2. Start with the excerpt; it shows the threads that are probably involved.
3. Download a full dump and open it with *Analyze Stack Trace or Thread Dump* (via *Search Everywhere*).
4. Find the event dispatch thread (`AWT-EventQueue-0` or `EDT`) and check whether it runs expensive code or waits for something else.
5. Compare the dumps of the same freeze: an operation that shows up in every dump is what holds up the UI.
6. With a coding agent, Patrick Scheibe's [analyze-freeze skill](https://gist.github.com/halirutan/3639e876b1faeba33200896b850a6842) helps to trace the connections between threads (coroutines, dispatcher starvation, read and write locks).
   Save it next to the dumps and ask the agent what stops the EDT.
   Check its explanation against the dumps and the code before changing anything.
7. If stuck, ask in the [JetBrains Platform Forum](https://platform.jetbrains.com/) with the dumps.

## Typical causes

- **Disk, network or processes on the EDT**, also inside a write action.
  For SDK Keeper the risky calls are `JavaSdk.createJdk` (detects the version and scans the class roots), `Path.toRealPath` and `Files.isDirectory` (a dead SDK home can be on an unplugged or network disk), HTTP requests to foojay and SDKMAN, and `sdk` processes.
- **Long non-cancellable read actions in the background** (`ReadAction.compute`, `runReadAction`): a pending write action waits for them, and the EDT waits for the write action.
  Prefer the coroutine `readAction { }` or `ReadAction.nonBlocking { }`, which restart when a write is pending.
- **The EDT waiting for background work**, e.g. `runBlocking` or `CompletableFuture.join()` on the EDT.
- **An exhausted `Dispatchers.Default`**: its threads are shared with `Dispatchers.IO`, so blocking calls belong on `Dispatchers.IO`.

## How SDK Keeper avoids them

- A sync prepares everything in the background (`Dispatchers.IO`): it scans the JDKs, creates the new SDK objects and looks up dead homes and symlinks.
  The EDT only applies the prepared changes to the SDK table and the project settings.
- HTTP requests, the symlinks and `toolchains.xml` run on `Dispatchers.IO`; SDKMAN installations run as a background task.
- `.sdkmanrc` is read in a cancellable `readAction`.
