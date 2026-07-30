# Release candidate 1.0.0

## Stability fixes

- Fixed plugin enable failure caused by calling `Client.addChatMessage` outside RuneLite's client thread.
- Added a lifecycle guard so a queued welcome message cannot run after shutdown.
- Removed the development status/debug overlay from startup and from the release source tree.
- Removed the raw debug rows and verbose console logging configuration methods.
- Made the dimmer viewport and chat cut-outs canvas-safe.
- Prevented the `Unhide interfaces` option from falling back to an unexpected full-screen dim while viewport dimensions are temporarily unavailable.
- Restored graphics state after drawing the dimmer.

## Project cleanup

- Removed the unused `CargoDisplayMode` source file.
- Removed the unused Lombok build dependency.
- Removed the RuneLite `--debug` launch argument from the development run task.
- Updated plugin metadata and version to 1.0.0.
- Excluded local build output, IDE state, Gradle caches and JVM crash logs from the release archive.

## Preserved behaviour

The existing cargo reconciliation, salvaging calibration, moving-average rate, sorting ETA, wreck tracking, Crystal Extractor tracking, dimmer interaction rules and movable status overlay were not redesigned in this pass.
