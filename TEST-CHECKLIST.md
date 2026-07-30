# Release test checklist

## Startup and shutdown

- Enable the plugin while logged in. The switch should remain enabled.
- Confirm one green `WELCOME TO SALVAGING PLUGIN` game message appears.
- Disable and re-enable the plugin. Confirm there is no client-thread assertion.
- Hop worlds and confirm the plugin resumes without duplicating overlays.

## Salvaging

- Confirm the ETA begins with `Calibrating (8)` and counts down on confirmed salvage gains.
- Confirm unrelated inventory and interface clicks do not affect the dimmer.
- Click the cargo hold or Crystal Extractor while salvaging. Confirm the dimmer hides for three seconds.
- Click the salvaging hook during that period. Confirm the dimmer returns immediately.

## Cargo

- Open the cargo interface and confirm the displayed cargo synchronises.
- Withdraw salvage and keep the interface open long enough for the confirmed value to update. Confirm the withdrawal is not counted twice.
- Withdraw salvage and close the interface quickly. Confirm the inventory fallback subtracts the withdrawn amount once.
- Deposit salvage and confirm cargo reconciliation still works.

## Wrecks

- Confirm two newly observed wreck spawns complete wreck calibration.
- Confirm zero active wrecks displays `Respawn in X:XX max`, never `Wreck soon`.
- When salvaging a single identified wreck, confirm the Salvaging ETA uses that wreck's remaining life when it is sooner than inventory-full ETA.
- Confirm `Wreck soonish` appears for ten seconds after the tracked wreck ETA reaches zero, followed by `Wreck soon`.

## Sorting

- Confirm sorting activity displays a smooth Sorting ETA.
- Confirm the sorting dimmer appears only when enabled.
- Confirm the early-undim threshold hides the dimmer at the configured remaining time.

## Crystal Extractor

- Confirm the normal countdown appears while cooling down.
- After the countdown expires but before the ready animation, confirm `.`, `..`, `...` cycles.
- Confirm the ready text pulses blue when enabled.
- Confirm `Undim when extractor ready` hides the dimmer while the extractor is ready.

## Overlay and dimmer layout

- Test left, centre and right text alignment. The fixed-width block should not shift as numbers change.
- Test centre snapping.
- Test custom text, background and dimmer colours and opacity.
- Test `Unhide chat` in fixed and resizable layouts.
- Test `Unhide interfaces`. Only the playable viewport should dim.
- Test `Only show when dimmer active`.
